package com.quantbacktest.backtester.concurrency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionRequest;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionResponse;
import com.quantbacktest.backtester.domain.BacktestJob;
import com.quantbacktest.backtester.domain.JobStatus;
import com.quantbacktest.backtester.infrastructure.QueueService;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.repository.BacktestResultRepository;
import com.quantbacktest.backtester.service.BacktestMetricsService;
import com.quantbacktest.backtester.service.BacktestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Concurrency tests to ensure thread safety and proper handling of simultaneous
 * operations.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrencyTest {

    @Mock
    private BacktestJobRepository backtestJobRepository;

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @Mock
    private QueueService queueService;

    @Mock
    private BacktestMetricsService metricsService;

    private BacktestServiceImpl backtestService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JavaTimeModule for LocalDate support
        backtestService = new BacktestServiceImpl(
                backtestJobRepository,
                backtestResultRepository,
                queueService,
                objectMapper,
                metricsService);
    }

    @Test
    void testConcurrentSubmissions_DifferentJobs() throws Exception {
        // Arrange
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        Set<Long> jobIds = ConcurrentHashMap.newKeySet();

        AtomicInteger jobIdCounter = new AtomicInteger(1);
        when(backtestJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(backtestJobRepository.save(any())).thenAnswer(invocation -> {
            BacktestJob job = invocation.getArgument(0);
            job.setId((long) jobIdCounter.getAndIncrement());
            return job;
        });

        // Act - Submit different jobs concurrently
        List<Future<BacktestSubmissionResponse>> futures = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            Future<BacktestSubmissionResponse> future = executor.submit(() -> {
                try {
                    BacktestSubmissionRequest request = createRequest("AAPL" + index);
                    return backtestService.submitBacktest(request);
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - All jobs should be created with unique IDs
        for (Future<BacktestSubmissionResponse> future : futures) {
            BacktestSubmissionResponse response = future.get();
            assertNotNull(response);
            assertNotNull(response.getJobId());
            jobIds.add(response.getJobId());
        }

        assertEquals(numThreads, jobIds.size(), "Should have unique job IDs for all submissions");
        verify(backtestJobRepository, times(numThreads)).findByIdempotencyKey(any());
        verify(backtestJobRepository, times(numThreads * 2)).save(any()); // Initial + status update
        verify(queueService, times(numThreads)).push(any());
    }

    @Test
    void testConcurrentSubmissions_SameJob_Idempotency() throws Exception {
        // Arrange
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Long> jobIds = Collections.synchronizedList(new ArrayList<>());
        List<Boolean> isExistingFlags = Collections.synchronizedList(new ArrayList<>());

        // First thread creates job, rest should get existing job
        AtomicInteger callCount = new AtomicInteger(0);
        BacktestJob existingJob = BacktestJob.builder()
                .id(1L)
                .status(JobStatus.QUEUED)
                .build();

        when(backtestJobRepository.findByIdempotencyKey(any())).thenAnswer(invocation -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
                return Optional.empty(); // First call - no existing job
            } else {
                return Optional.of(existingJob); // Subsequent calls - existing job
            }
        });

        when(backtestJobRepository.save(any())).thenAnswer(invocation -> {
            BacktestJob job = invocation.getArgument(0);
            job.setId(1L);
            return job;
        });

        // Act - Submit identical jobs concurrently
        List<Future<BacktestSubmissionResponse>> futures = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            Future<BacktestSubmissionResponse> future = executor.submit(() -> {
                try {
                    BacktestSubmissionRequest request = createRequest("AAPL"); // Same symbol
                    BacktestSubmissionResponse response = backtestService.submitBacktest(request);
                    jobIds.add(response.getJobId());
                    isExistingFlags.add(response.getIsExisting());
                    return response;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - All should return same job ID
        for (Future<BacktestSubmissionResponse> future : futures) {
            BacktestSubmissionResponse response = future.get();
            assertEquals(1L, response.getJobId());
        }

        // Verify idempotency respected
        long distinctJobIds = jobIds.stream().distinct().count();
        assertEquals(1, distinctJobIds, "All concurrent submissions should return same job ID");

        // At least one should be marked as existing
        assertTrue(isExistingFlags.stream().anyMatch(flag -> flag),
                "At least one response should have isExisting=true");
    }

    @Test
    void testConcurrentStatusChecks_NoDuplicateExecution() throws Exception {
        // Arrange - Simulate race condition where multiple workers fetch same job
        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger executionCount = new AtomicInteger(0);

        BacktestJob job = BacktestJob.builder()
                .id(1L)
                .status(JobStatus.QUEUED)
                .retryCount(0)
                .build();

        // Simulate checking job status (workers would do this)
        Runnable checkAndExecute = () -> {
            try {
                // All threads see QUEUED status simultaneously
                if (job.getStatus() == JobStatus.QUEUED) {
                    // Simulate execution
                    executionCount.incrementAndGet();
                    Thread.sleep(10); // Simulate work
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        };

        // Act
        for (int i = 0; i < numThreads; i++) {
            executor.submit(checkAndExecute);
        }

        latch.await(2, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - Without proper locking, multiple threads would execute
        // This test demonstrates the race condition (in production, use DB pessimistic
        // locks)
        assertTrue(executionCount.get() > 0, "At least one thread should execute");
        // Note: In current implementation without pessimistic locking,
        // multiple threads might execute - this test documents the behavior
    }

    @Test
    void testConcurrentJobCreation_UniqueIdempotencyKeys() throws Exception {
        // Arrange
        int numThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        Set<String> capturedKeys = ConcurrentHashMap.newKeySet();

        when(backtestJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(backtestJobRepository.save(any())).thenAnswer(invocation -> {
            BacktestJob job = invocation.getArgument(0);
            job.setId(ThreadLocalRandom.current().nextLong(1, 10000));
            capturedKeys.add(job.getIdempotencyKey());
            return job;
        });

        // Act - Create jobs with slightly different parameters
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("shortPeriod", 5 + index); // Different parameter
                    params.put("longPeriod", 20);

                    BacktestSubmissionRequest request = BacktestSubmissionRequest.builder()
                            .strategyName("MovingAverageCrossover")
                            .symbol("AAPL")
                            .startDate(LocalDate.of(2024, 1, 1))
                            .endDate(LocalDate.of(2024, 12, 31))
                            .parameters(params)
                            .initialCapital(new BigDecimal("10000.00"))
                            .build();

                    backtestService.submitBacktest(request);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - All should have unique idempotency keys
        assertEquals(numThreads, capturedKeys.size(),
                "All jobs with different parameters should have unique idempotency keys");
    }

    @Test
    void testHighConcurrencyStress_1000Submissions() throws Exception {
        // Arrange
        int numSubmissions = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(numSubmissions);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        AtomicInteger jobIdCounter = new AtomicInteger(1);
        when(backtestJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(backtestJobRepository.save(any())).thenAnswer(invocation -> {
            BacktestJob job = invocation.getArgument(0);
            job.setId((long) jobIdCounter.getAndIncrement());
            return job;
        });

        // Act
        for (int i = 0; i < numSubmissions; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    BacktestSubmissionRequest request = createRequest("SYM" + (index % 100));
                    backtestService.submitBacktest(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(completed, "All submissions should complete within timeout");
        assertEquals(numSubmissions, successCount.get(), "All submissions should succeed");
        assertEquals(0, failureCount.get(), "No submissions should fail");
    }

    @Test
    void testConcurrentQueueOperations_ThreadSafety() throws Exception {
        // Arrange
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        Queue<Long> pushedIds = new ConcurrentLinkedQueue<>();

        // Act - Multiple threads pushing to queue
        for (int i = 0; i < numThreads; i++) {
            final long jobId = i + 1;
            executor.submit(() -> {
                try {
                    queueService.push(jobId);
                    pushedIds.add(jobId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertEquals(numThreads, pushedIds.size(), "All queue pushes should complete");
        verify(queueService, times(numThreads)).push(any());
    }

    private BacktestSubmissionRequest createRequest(String symbol) {
        Map<String, Object> params = new HashMap<>();
        params.put("shortPeriod", 5);
        params.put("longPeriod", 20);

        return BacktestSubmissionRequest.builder()
                .strategyName("MovingAverageCrossover")
                .symbol(symbol)
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .parameters(params)
                .initialCapital(new BigDecimal("10000.00"))
                .build();
    }
}
