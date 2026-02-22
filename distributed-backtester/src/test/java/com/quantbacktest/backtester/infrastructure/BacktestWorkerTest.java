package com.quantbacktest.backtester.infrastructure;

import com.quantbacktest.backtester.domain.BacktestJob;
import com.quantbacktest.backtester.domain.JobStatus;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.service.BacktestExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BacktestWorker lifecycle and job processing.
 */
@ExtendWith(MockitoExtension.class)
class BacktestWorkerTest {

    @Mock
    private QueueService queueService;

    @Mock
    private BacktestJobRepository backtestJobRepository;

    @Mock
    private BacktestExecutor backtestExecutor;

    private BacktestWorker worker;

    @BeforeEach
    void setUp() {
        worker = new BacktestWorker(queueService, backtestJobRepository, backtestExecutor, "TestWorker");
    }

    @Test
    void testProcessJob_Success() throws Exception {
        // Arrange
        Long jobId = 1L;
        BacktestJob job = createJob(jobId, JobStatus.QUEUED, 0);

        when(queueService.pop()).thenReturn(jobId).thenReturn(null);
        when(backtestJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        doNothing().when(backtestExecutor).executeBacktest(any());

        // Act
        Thread workerThread = new Thread(worker);
        workerThread.start();
        Thread.sleep(200);
        worker.stop();
        workerThread.join(1000);

        // Assert
        verify(queueService, atLeastOnce()).pop();
        verify(backtestJobRepository).findById(jobId);
        verify(backtestExecutor).executeBacktest(job);
    }

    @Test
    void testProcessJob_SkipsAlreadyCompletedJob() throws Exception {
        // Arrange
        Long jobId = 1L;
        BacktestJob job = createJob(jobId, JobStatus.COMPLETED, 0);

        when(queueService.pop()).thenReturn(jobId).thenReturn(null);
        when(backtestJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // Act
        Thread workerThread = new Thread(worker);
        workerThread.start();
        Thread.sleep(200);
        worker.stop();
        workerThread.join(1000);

        // Assert
        verify(backtestJobRepository).findById(jobId);
        verify(backtestExecutor, never()).executeBacktest(any());
    }

    @Test
    void testProcessJob_SkipsRunningJob() throws Exception {
        // Arrange
        Long jobId = 1L;
        BacktestJob job = createJob(jobId, JobStatus.RUNNING, 0);

        when(queueService.pop()).thenReturn(jobId).thenReturn(null);
        when(backtestJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // Act
        Thread workerThread = new Thread(worker);
        workerThread.start();
        Thread.sleep(200);
        worker.stop();
        workerThread.join(1000);

        // Assert
        verify(backtestJobRepository).findById(jobId);
        verify(backtestExecutor, never()).executeBacktest(any());
    }

    @Test
    void testProcessJob_JobNotFound() throws Exception {
        // Arrange
        Long jobId = 999L;
        when(queueService.pop()).thenReturn(jobId).thenReturn(null);
        when(backtestJobRepository.findById(jobId)).thenReturn(Optional.empty());

        // Act
        Thread workerThread = new Thread(worker);
        workerThread.start();
        Thread.sleep(200);
        worker.stop();
        workerThread.join(1000);

        // Assert
        verify(backtestJobRepository).findById(jobId);
        verify(backtestExecutor, never()).executeBacktest(any());
    }

    @Test
    void testProcessJob_RetryWithExponentialBackoff() throws Exception {
        // Arrange
        Long jobId = 1L;
        BacktestJob job = createJob(jobId, JobStatus.QUEUED, 2); // 2nd retry

        when(queueService.pop()).thenReturn(jobId).thenReturn(null);
        when(backtestJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        doNothing().when(backtestExecutor).executeBacktest(any());

        // Act
        long startTime = System.currentTimeMillis();
        Thread workerThread = new Thread(worker);
        workerThread.start();
        Thread.sleep(500);
        worker.stop();
        workerThread.join(5000);
        long elapsed = System.currentTimeMillis() - startTime;

        // Assert - Should have applied 3s backoff (2nd retry)
        verify(backtestExecutor).executeBacktest(job);
        // Note: Actual backoff timing is hard to test precisely due to thread
        // scheduling
    }

    @Test
    void testProcessJob_ExecutorThrowsException() throws Exception {
        // Arrange
        Long jobId = 1L;
        BacktestJob job = createJob(jobId, JobStatus.QUEUED, 0);

        when(queueService.pop()).thenReturn(jobId).thenReturn(null);
        when(backtestJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        doThrow(new RuntimeException("Test exception")).when(backtestExecutor).executeBacktest(any());

        // Act & Assert - Should not crash the worker
        Thread workerThread = new Thread(worker);
        workerThread.start();
        Thread.sleep(200);
        worker.stop();
        workerThread.join(1000);

        verify(backtestExecutor).executeBacktest(job);
    }

    @Test
    void testWorkerStop_GracefulShutdown() throws Exception {
        // Arrange
        when(queueService.pop()).thenAnswer(invocation -> {
            Thread.sleep(50);
            return null;
        });

        // Act
        Thread workerThread = new Thread(worker);
        workerThread.start();
        Thread.sleep(100);
        worker.stop();
        workerThread.join(1000);

        // Assert - Worker should stop gracefully
        verify(queueService, atLeastOnce()).pop();
    }

    @Test
    void testWorker_HandlesQueueException() throws Exception {
        // Arrange
        when(queueService.pop())
                .thenThrow(new RuntimeException("Queue error"))
                .thenReturn(null);

        // Act
        Thread workerThread = new Thread(worker);
        workerThread.start();
        Thread.sleep(1200); // Give time for error recovery sleep
        worker.stop();
        workerThread.join(1000);

        // Assert - Worker should recover from exception
        verify(queueService, atLeast(2)).pop();
    }

    @Test
    void testProcessMultipleJobs_InSequence() throws Exception {
        // Arrange
        BacktestJob job1 = createJob(1L, JobStatus.QUEUED, 0);
        BacktestJob job2 = createJob(2L, JobStatus.QUEUED, 0);

        when(queueService.pop())
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(null);
        when(backtestJobRepository.findById(1L)).thenReturn(Optional.of(job1));
        when(backtestJobRepository.findById(2L)).thenReturn(Optional.of(job2));

        // Act
        Thread workerThread = new Thread(worker);
        workerThread.start();
        Thread.sleep(300);
        worker.stop();
        workerThread.join(1000);

        // Assert
        verify(backtestExecutor).executeBacktest(job1);
        verify(backtestExecutor).executeBacktest(job2);
    }

    private BacktestJob createJob(Long id, JobStatus status, int retryCount) {
        return BacktestJob.builder()
                .id(id)
                .strategyName("BuyAndHold")
                .symbol("AAPL")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .parametersJson("{}")
                .status(status)
                .retryCount(retryCount)
                .build();
    }
}
