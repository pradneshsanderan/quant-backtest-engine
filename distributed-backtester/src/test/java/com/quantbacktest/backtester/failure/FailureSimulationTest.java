package com.quantbacktest.backtester.failure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbacktest.backtester.domain.*;
import com.quantbacktest.backtester.infrastructure.QueueService;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.repository.BacktestResultRepository;
import com.quantbacktest.backtester.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for failure scenarios and retry logic.
 */
@ExtendWith(MockitoExtension.class)
class FailureSimulationTest {

    @Mock
    private BacktestJobRepository backtestJobRepository;

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @Mock
    private QueueService queueService;

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private StrategyFactory strategyFactory;

    @Mock
    private ParameterSweepService parameterSweepService;

    @Mock
    private BacktestMetricsService metricsService;

    private BacktestExecutorImpl backtestExecutor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        backtestExecutor = new BacktestExecutorImpl(
                backtestJobRepository,
                backtestResultRepository,
                queueService,
                marketDataService,
                strategyFactory,
                objectMapper,
                parameterSweepService,
                metricsService);
    }

    @Test
    void testExecuteBacktest_RuntimeException_RetriesJob() {
        // Arrange
        BacktestJob job = createJob(1L, JobStatus.QUEUED, 0);
        when(marketDataService.loadMarketData(any(), any(), any()))
                .thenThrow(new RuntimeException("Network timeout"));

        // Act
        backtestExecutor.executeBacktest(job);

        // Assert
        ArgumentCaptor<BacktestJob> jobCaptor = ArgumentCaptor.forClass(BacktestJob.class);
        verify(backtestJobRepository, atLeast(2)).save(jobCaptor.capture());

        BacktestJob savedJob = jobCaptor.getAllValues().get(jobCaptor.getAllValues().size() - 1);
        assertEquals(1, savedJob.getRetryCount(), "Retry count should be incremented");
        assertEquals(JobStatus.QUEUED, savedJob.getStatus(), "Job should be requeued");
        assertEquals("Network timeout", savedJob.getFailureReason());

        verify(queueService).push(job.getId());
        verify(metricsService).recordJobRetried();
        verify(backtestResultRepository, never()).save(any());
    }

    @Test
    void testExecuteBacktest_SecondRetry_Success() {
        // Arrange
        BacktestJob job = createJob(1L, JobStatus.QUEUED, 1); // Already retried once
        List<MarketData> marketData = createMockMarketData();
        Strategy strategy = new BuyAndHoldStrategy();

        when(marketDataService.loadMarketData(any(), any(), any())).thenReturn(marketData);
        when(strategyFactory.createStrategy(any(), any())).thenReturn(strategy);

        // Act
        backtestExecutor.executeBacktest(job);

        // Assert
        ArgumentCaptor<BacktestJob> jobCaptor = ArgumentCaptor.forClass(BacktestJob.class);
        verify(backtestJobRepository, atLeast(2)).save(jobCaptor.capture());

        // Find the final saved job
        BacktestJob finalJob = jobCaptor.getAllValues().stream()
                .reduce((first, second) -> second)
                .orElse(null);

        assertNotNull(finalJob);
        assertEquals(JobStatus.COMPLETED, finalJob.getStatus(), "Job should be completed on retry success");
        assertEquals(1, finalJob.getRetryCount(), "Retry count should remain 1");

        verify(backtestResultRepository).save(any());
        verify(metricsService).recordJobCompleted(anyLong());
    }

    @Test
    void testExecuteBacktest_MaxRetriesExceeded_MarkedFailed() {
        // Arrange
        BacktestJob job = createJob(1L, JobStatus.QUEUED, 2); // Already retried twice
        when(marketDataService.loadMarketData(any(), any(), any()))
                .thenThrow(new RuntimeException("Persistent failure"));

        // Act
        backtestExecutor.executeBacktest(job);

        // Assert
        ArgumentCaptor<BacktestJob> jobCaptor = ArgumentCaptor.forClass(BacktestJob.class);
        verify(backtestJobRepository, atLeast(2)).save(jobCaptor.capture());

        BacktestJob savedJob = jobCaptor.getAllValues().get(jobCaptor.getAllValues().size() - 1);
        assertEquals(3, savedJob.getRetryCount(), "Retry count should be 3");
        assertEquals(JobStatus.FAILED, savedJob.getStatus(), "Job should be marked FAILED");
        assertEquals("Persistent failure", savedJob.getFailureReason());

        verify(queueService, never()).push(job.getId()); // Should NOT requeue
        verify(metricsService).recordJobFailed();
        verify(metricsService, never()).recordJobRetried();
        verify(backtestResultRepository, never()).save(any());
    }

    @Test
    void testExecuteBacktest_NullPointerException_HandledGracefully() {
        // Arrange
        BacktestJob job = createJob(1L, JobStatus.QUEUED, 0);
        when(marketDataService.loadMarketData(any(), any(), any()))
                .thenThrow(new NullPointerException("Unexpected null"));

        // Act - Should not crash
        assertDoesNotThrow(() -> backtestExecutor.executeBacktest(job));

        // Assert
        verify(backtestJobRepository, atLeast(2)).save(any());
        verify(queueService).push(job.getId()); // Should requeue for retry
    }

    @Test
    void testExecuteBacktest_EmptyMarketData_Fails() {
        // Arrange
        BacktestJob job = createJob(1L, JobStatus.QUEUED, 0);
        when(marketDataService.loadMarketData(any(), any(), any()))
                .thenReturn(new ArrayList<>()); // Empty list

        // Act
        backtestExecutor.executeBacktest(job);

        // Assert
        ArgumentCaptor<BacktestJob> jobCaptor = ArgumentCaptor.forClass(BacktestJob.class);
        verify(backtestJobRepository, atLeast(2)).save(jobCaptor.capture());

        BacktestJob savedJob = jobCaptor.getAllValues().get(jobCaptor.getAllValues().size() - 1);
        assertEquals(1, savedJob.getRetryCount());
        assertEquals(JobStatus.QUEUED, savedJob.getStatus());
        assertTrue(savedJob.getFailureReason().contains("No market data"));

        verify(queueService).push(job.getId());
    }

    @Test
    void testExecuteBacktest_StrategyCreationFails_Retries() {
        // Arrange
        BacktestJob job = createJob(1L, JobStatus.QUEUED, 0);
        List<MarketData> marketData = createMockMarketData();

        when(marketDataService.loadMarketData(any(), any(), any())).thenReturn(marketData);
        when(strategyFactory.createStrategy(any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid strategy parameters"));

        // Act
        backtestExecutor.executeBacktest(job);

        // Assert
        ArgumentCaptor<BacktestJob> jobCaptor = ArgumentCaptor.forClass(BacktestJob.class);
        verify(backtestJobRepository, atLeast(2)).save(jobCaptor.capture());

        BacktestJob savedJob = jobCaptor.getAllValues().get(jobCaptor.getAllValues().size() - 1);
        assertEquals(JobStatus.QUEUED, savedJob.getStatus());
        assertTrue(savedJob.getFailureReason().contains("Invalid strategy parameters"));
    }

    @Test
    void testExecuteBacktest_AlreadyCompleted_SkipsExecution() {
        // Arrange
        BacktestJob job = createJob(1L, JobStatus.COMPLETED, 0);

        // Act
        backtestExecutor.executeBacktest(job);

        // Assert
        verify(backtestJobRepository, never()).save(any());
        verify(marketDataService, never()).loadMarketData(any(), any(), any());
        verify(backtestResultRepository, never()).save(any());
    }

    @Test
    void testExecuteBacktest_AlreadyRunning_SkipsExecution() {
        // Arrange
        BacktestJob job = createJob(1L, JobStatus.RUNNING, 0);

        // Act
        backtestExecutor.executeBacktest(job);

        // Assert
        verify(backtestJobRepository, never()).save(any());
        verify(marketDataService, never()).loadMarketData(any(), any(), any());
    }

    @Test
    void testExecuteBacktest_DatabaseSaveFailure_DoesNotSwallow() {
        // Arrange
        BacktestJob job = createJob(1L, JobStatus.QUEUED, 0);

        when(backtestResultRepository.save(any()))
                .thenThrow(new RuntimeException("Database connection lost"));

        // Act
        backtestExecutor.executeBacktest(job);

        // Assert - Should handle failure and retry
        verify(backtestJobRepository, atLeast(2)).save(any());
        verify(queueService).push(job.getId());
    }

    @Test
    void testExecuteBacktest_FailureWithParentSweep_UpdatesSweepProgress() {
        // Arrange
        BacktestJob job = createJob(1L, JobStatus.QUEUED, 2); // Max retries
        job.setParentSweepJobId(100L);

        when(marketDataService.loadMarketData(any(), any(), any()))
                .thenThrow(new RuntimeException("Fatal error"));

        // Act
        backtestExecutor.executeBacktest(job);

        // Assert
        verify(parameterSweepService).checkSweepProgress(100L);
        verify(metricsService).recordJobFailed();
    }

    @Test
    void testExecuteBacktest_RetrySequence_CorrectStatusTransitions() {
        // Arrange - Simulate 3 failures then success
        BacktestJob job = createJob(1L, JobStatus.QUEUED, 0);

        when(marketDataService.loadMarketData(any(), any(), any()))
                .thenThrow(new RuntimeException("Attempt 1 failed"))
                .thenThrow(new RuntimeException("Attempt 2 failed"))
                .thenThrow(new RuntimeException("Attempt 3 failed"));

        // Act - Execute 3 times (will fail each time)
        backtestExecutor.executeBacktest(job); // Attempt 1 - fails, retry count = 1
        assertEquals(1, job.getRetryCount());

        backtestExecutor.executeBacktest(job); // Attempt 2 - fails, retry count = 2
        assertEquals(2, job.getRetryCount());

        backtestExecutor.executeBacktest(job); // Attempt 3 - fails, retry count = 3
        assertEquals(3, job.getRetryCount());

        // Assert - After 3 failures, should be marked FAILED
        ArgumentCaptor<BacktestJob> jobCaptor = ArgumentCaptor.forClass(BacktestJob.class);
        verify(backtestJobRepository, atLeast(6)).save(jobCaptor.capture());

        BacktestJob finalJob = jobCaptor.getAllValues().get(jobCaptor.getAllValues().size() - 1);
        assertEquals(JobStatus.FAILED, finalJob.getStatus());
        assertEquals(3, finalJob.getRetryCount());

        verify(metricsService, times(2)).recordJobRetried(); // First 2 retries
        verify(metricsService).recordJobFailed(); // Final failure
    }

    @Test
    void testExecuteBacktest_IntermittentFailures_EventuallySucceeds() {
        // Arrange
        BacktestJob job = createJob(1L, JobStatus.QUEUED, 1); // Second attempt
        List<MarketData> marketData = createMockMarketData();
        Strategy strategy = new BuyAndHoldStrategy();

        // First call fails, second succeeds
        when(marketDataService.loadMarketData(any(), any(), any()))
                .thenThrow(new RuntimeException("Transient error"))
                .thenReturn(marketData);

        when(strategyFactory.createStrategy(any(), any())).thenReturn(strategy);

        // Act - First execution fails
        backtestExecutor.executeBacktest(job);
        assertEquals(2, job.getRetryCount());
        assertEquals(JobStatus.QUEUED, job.getStatus());

        // Second execution succeeds
        backtestExecutor.executeBacktest(job);

        // Assert
        verify(backtestResultRepository).save(any());
        verify(metricsService).recordJobCompleted(anyLong());
    }

    private BacktestJob createJob(Long id, JobStatus status, int retryCount) {
        return BacktestJob.builder()
                .id(id)
                .strategyName("BuyAndHold")
                .symbol("AAPL")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .parametersJson("{\"initialCapital\": \"10000.00\"}")
                .status(status)
                .retryCount(retryCount)
                .build();
    }

    private List<MarketData> createMockMarketData() {
        List<MarketData> data = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            data.add(MarketData.builder()
                    .date(LocalDate.of(2024, 1, 1).plusDays(i))
                    .symbol("AAPL")
                    .open(new BigDecimal("100.00"))
                    .high(new BigDecimal("105.00"))
                    .low(new BigDecimal("98.00"))
                    .close(new BigDecimal("102.00"))
                    .volume(1000000L)
                    .build());
        }
        return data;
    }
}
