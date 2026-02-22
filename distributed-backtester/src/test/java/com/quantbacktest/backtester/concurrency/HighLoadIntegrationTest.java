package com.quantbacktest.backtester.concurrency;

import com.quantbacktest.backtester.controller.dto.BacktestSubmissionRequest;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionResponse;
import com.quantbacktest.backtester.domain.BacktestJob;
import com.quantbacktest.backtester.domain.BacktestResult;
import com.quantbacktest.backtester.domain.JobStatus;
import com.quantbacktest.backtester.infrastructure.QueueService;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.repository.BacktestResultRepository;
import com.quantbacktest.backtester.service.BacktestExecutor;
import com.quantbacktest.backtester.service.BacktestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * High-load integration test simulating 100 concurrent backtest job
 * submissions.
 * 
 * This test verifies:
 * - All jobs complete successfully
 * - No duplicate executions occur
 * - No status inconsistencies
 * - No data corruption
 * - System performance under load
 * 
 * Note: This test uses mocked market data and execution to focus on
 * concurrency and system integrity rather than actual backtest logic.
 */
@SpringBootTest
@ActiveProfiles("test")
class HighLoadIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(HighLoadIntegrationTest.class);

    private static final int NUM_JOBS = 100;
    private static final int THREAD_POOL_SIZE = 20;
    private static final int MAX_WAIT_MINUTES = 5;
    private static final String SEPARATOR = "================================================================================";

    @Autowired
    private BacktestService backtestService;

    @Autowired
    private BacktestJobRepository jobRepository;

    @Autowired
    private BacktestResultRepository resultRepository;

    @MockBean
    private QueueService queueService;

    @MockBean
    private BacktestExecutor backtestExecutor;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        resultRepository.deleteAll();
        jobRepository.deleteAll();

        // Mock executor to complete jobs immediately with simulated results
        doAnswer(invocation -> {
            BacktestJob job = invocation.getArgument(0);
            Long jobId = job.getId();

            // Simulate processing delay (10-50ms)
            Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));

            // Reload job from database to avoid detached entity issues
            BacktestJob managedJob = jobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalStateException("Job not found"));

            // Update job status to RUNNING
            managedJob.setStatus(JobStatus.RUNNING);
            jobRepository.saveAndFlush(managedJob);

            // Simulate backtest execution
            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 150));

            // Reload again to ensure we have the latest state
            managedJob = jobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalStateException("Job not found"));

            // Create mock result
            BacktestResult result = BacktestResult.builder()
                    .job(managedJob)
                    .totalReturn(new BigDecimal("15.5"))
                    .cagr(new BigDecimal("12.3"))
                    .volatility(new BigDecimal("18.2"))
                    .sharpeRatio(new BigDecimal("1.25"))
                    .sortinoRatio(new BigDecimal("1.45"))
                    .maxDrawdown(new BigDecimal("-8.3"))
                    .winRate(new BigDecimal("55.5"))
                    .executionTimeMs(100L)
                    .resultJson("[]")
                    .build();
            resultRepository.saveAndFlush(result);

            // Reload one more time and mark job as completed
            managedJob = jobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalStateException("Job not found"));
            managedJob.setStatus(JobStatus.COMPLETED);
            jobRepository.saveAndFlush(managedJob);

            return null;
        }).when(backtestExecutor).executeBacktest(any(BacktestJob.class));
    }

    @Test
    @Timeout(value = MAX_WAIT_MINUTES, unit = TimeUnit.MINUTES)
    void testHighLoad_100ConcurrentJobs() throws Exception {
        log.info(SEPARATOR);
        log.info("HIGH LOAD TEST: Starting 100 concurrent job submissions");
        log.info(SEPARATOR);

        Instant startTime = Instant.now();

        // Phase 1: Submit 100 jobs concurrently
        log.info("Phase 1: Submitting {} jobs concurrently with {} threads",
                NUM_JOBS, THREAD_POOL_SIZE);

        List<BacktestSubmissionResponse> responses = submitJobsConcurrently();

        Instant submissionEndTime = Instant.now();
        Duration submissionDuration = Duration.between(startTime, submissionEndTime);

        log.info("Phase 1 Complete: All {} jobs submitted in {} ms",
                NUM_JOBS, submissionDuration.toMillis());

        // Phase 2: Process jobs (simulate workers)
        log.info("Phase 2: Processing jobs (simulated workers)");

        processAllJobs(responses);

        Instant processingEndTime = Instant.now();
        Duration processingDuration = Duration.between(submissionEndTime, processingEndTime);

        log.info("Phase 2 Complete: All {} jobs processed in {} ms",
                NUM_JOBS, processingDuration.toMillis());

        // Phase 3: Verify results
        log.info("Phase 3: Verifying system integrity");

        verifySystemIntegrity(responses);

        Instant verificationEndTime = Instant.now();
        Duration totalDuration = Duration.between(startTime, verificationEndTime);

        // Log final statistics
        logFinalStatistics(totalDuration, submissionDuration, processingDuration);

        log.info(SEPARATOR);
        log.info("HIGH LOAD TEST: PASSED - All verifications successful");
        log.info(SEPARATOR);
    }

    /**
     * Submit jobs concurrently using thread pool executor.
     */
    private List<BacktestSubmissionResponse> submitJobsConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(NUM_JOBS);

        List<Future<BacktestSubmissionResponse>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Submit jobs
        for (int i = 0; i < NUM_JOBS; i++) {
            final int jobIndex = i;
            Future<BacktestSubmissionResponse> future = executor.submit(() -> {
                try {
                    BacktestSubmissionRequest request = createRequest(jobIndex);
                    BacktestSubmissionResponse response = backtestService.submitBacktest(request);
                    successCount.incrementAndGet();
                    return response;
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("Failed to submit job {}: {}", jobIndex, e.getMessage());
                    throw e;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Wait for all submissions
        boolean completed = latch.await(2, TimeUnit.MINUTES);
        assertTrue(completed, "All job submissions should complete within timeout");

        executor.shutdown();

        // Collect responses
        List<BacktestSubmissionResponse> responses = new ArrayList<>();
        for (Future<BacktestSubmissionResponse> future : futures) {
            responses.add(future.get());
        }

        log.info("Submission stats: Success={}, Failure={}",
                successCount.get(), failureCount.get());

        assertEquals(NUM_JOBS, successCount.get(), "All submissions should succeed");
        assertEquals(0, failureCount.get(), "No submissions should fail");

        return responses;
    }

    /**
     * Process all queued jobs by simulating worker execution.
     */
    private void processAllJobs(List<BacktestSubmissionResponse> responses) throws Exception {
        ExecutorService workerPool = Executors.newFixedThreadPool(10);
        CountDownLatch processingLatch = new CountDownLatch(NUM_JOBS);

        AtomicInteger processedCount = new AtomicInteger(0);

        // Simulate workers processing jobs
        for (BacktestSubmissionResponse response : responses) {
            workerPool.submit(() -> {
                try {
                    processJobInTransaction(response.getJobId());
                    processedCount.incrementAndGet();

                    if (processedCount.get() % 10 == 0) {
                        log.info("Progress: {}/{} jobs processed",
                                processedCount.get(), NUM_JOBS);
                    }
                } catch (Exception e) {
                    log.error("Failed to process job {}: {}",
                            response.getJobId(), e.getMessage());
                } finally {
                    processingLatch.countDown();
                }
            });
        }

        boolean completed = processingLatch.await(3, TimeUnit.MINUTES);
        assertTrue(completed, "All jobs should be processed within timeout");

        workerPool.shutdown();

        log.info("All {} jobs processed", processedCount.get());
    }

    /**
     * Process a job within a transaction to ensure proper database state.
     */
    @Transactional
    public void processJobInTransaction(Long jobId) {
        BacktestJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found"));

        backtestExecutor.executeBacktest(job);
    }

    /**
     * Verify system integrity after high-load test.
     */
    private void verifySystemIntegrity(List<BacktestSubmissionResponse> responses) {
        log.info("Verifying: Job completion");
        verifyAllJobsCompleted(responses);

        log.info("Verifying: No duplicate executions");
        verifyNoDuplicateExecutions(responses);

        log.info("Verifying: Status consistency");
        verifyStatusConsistency();

        log.info("Verifying: Data integrity");
        verifyDataIntegrity();

        log.info("Verifying: Idempotency keys");
        verifyIdempotencyKeys();
    }

    /**
     * Verify all jobs reached COMPLETED status.
     */
    private void verifyAllJobsCompleted(List<BacktestSubmissionResponse> responses) {
        for (BacktestSubmissionResponse response : responses) {
            BacktestJob job = jobRepository.findById(response.getJobId())
                    .orElseThrow(() -> new AssertionError("Job not found: " + response.getJobId()));

            assertEquals(JobStatus.COMPLETED, job.getStatus(),
                    "Job " + job.getId() + " should be COMPLETED");
        }

        long completedCount = jobRepository.findByStatus(JobStatus.COMPLETED).size();
        assertEquals(NUM_JOBS, completedCount,
                "All jobs should have COMPLETED status");

        log.info("✓ All {} jobs completed successfully", NUM_JOBS);
    }

    /**
     * Verify no jobs were executed multiple times.
     */
    private void verifyNoDuplicateExecutions(List<BacktestSubmissionResponse> responses) {
        // Check each job has exactly one result
        for (BacktestSubmissionResponse response : responses) {
            List<BacktestResult> results = resultRepository.findAllByJobId(response.getJobId());

            assertEquals(1, results.size(),
                    "Job " + response.getJobId() + " should have exactly 1 result, found: " + results.size());
        }

        long totalResults = resultRepository.count();
        assertEquals(NUM_JOBS, totalResults,
                "Should have exactly " + NUM_JOBS + " results");

        log.info("✓ No duplicate executions detected");
    }

    /**
     * Verify no status inconsistencies (e.g., COMPLETED without result).
     */
    private void verifyStatusConsistency() {
        List<BacktestJob> completedJobs = jobRepository.findByStatus(JobStatus.COMPLETED);

        for (BacktestJob job : completedJobs) {
            Optional<BacktestResult> result = resultRepository.findByJobId(job.getId());
            assertTrue(result.isPresent(),
                    "Job " + job.getId() + " is COMPLETED but has no result");
        }

        // Verify no jobs stuck in intermediate states
        long queuedCount = jobRepository.findByStatus(JobStatus.QUEUED).size();
        long runningCount = jobRepository.findByStatus(JobStatus.RUNNING).size();
        long failedCount = jobRepository.findByStatus(JobStatus.FAILED).size();

        assertEquals(0, queuedCount, "No jobs should remain QUEUED");
        assertEquals(0, runningCount, "No jobs should remain RUNNING");
        assertEquals(0, failedCount, "No jobs should be FAILED");

        log.info("✓ No status inconsistencies found");
    }

    /**
     * Verify data integrity (no null values, valid metrics).
     */
    private void verifyDataIntegrity() {
        List<BacktestResult> allResults = resultRepository.findAll();

        assertEquals(NUM_JOBS, allResults.size(), "Should have results for all jobs");

        for (BacktestResult result : allResults) {
            // Verify all critical fields are present
            assertNotNull(result.getJob(), "Result must have associated job");
            assertNotNull(result.getTotalReturn(), "Total return must not be null");
            assertNotNull(result.getSharpeRatio(), "Sharpe ratio must not be null");
            assertNotNull(result.getMaxDrawdown(), "Max drawdown must not be null");

            // Verify metrics are in valid ranges
            assertTrue(result.getTotalReturn().compareTo(new BigDecimal("-100")) > 0,
                    "Total return should be reasonable");
            assertTrue(result.getMaxDrawdown().compareTo(new BigDecimal("-100")) >= 0,
                    "Max drawdown should be between 0 and -100");
        }

        log.info("✓ Data integrity verified - all fields valid");
    }

    /**
     * Verify idempotency keys are unique and properly set.
     */
    private void verifyIdempotencyKeys() {
        List<BacktestJob> allJobs = jobRepository.findAll();

        Set<String> idempotencyKeys = allJobs.stream()
                .map(BacktestJob::getIdempotencyKey)
                .collect(Collectors.toSet());

        // All keys should be non-null and non-empty
        for (BacktestJob job : allJobs) {
            assertNotNull(job.getIdempotencyKey(),
                    "Job " + job.getId() + " has null idempotency key");
            assertFalse(job.getIdempotencyKey().isBlank(),
                    "Job " + job.getId() + " has blank idempotency key");
        }

        // All keys should be unique (different parameters = different keys)
        assertEquals(NUM_JOBS, idempotencyKeys.size(),
                "All jobs should have unique idempotency keys");

        log.info("✓ All idempotency keys are unique and valid");
    }

    /**
     * Log final test statistics.
     */
    private void logFinalStatistics(Duration total, Duration submission, Duration processing) {
        log.info("");
        log.info(SEPARATOR);
        log.info("FINAL STATISTICS");
        log.info(SEPARATOR);
        log.info("Total Jobs:           {}", NUM_JOBS);
        log.info("Thread Pool Size:     {}", THREAD_POOL_SIZE);
        log.info("");
        log.info("Submission Time:      {} ms ({} ms/job)",
                submission.toMillis(), submission.toMillis() / NUM_JOBS);
        log.info("Processing Time:      {} ms ({} ms/job)",
                processing.toMillis(), processing.toMillis() / NUM_JOBS);
        log.info("Total Time:           {} ms", total.toMillis());
        log.info("");
        log.info("Throughput:           {:.2f} jobs/second",
                NUM_JOBS / (total.toMillis() / 1000.0));
        log.info("");

        // Database statistics
        long totalJobs = jobRepository.count();
        long totalResults = resultRepository.count();
        long completedJobs = jobRepository.findByStatus(JobStatus.COMPLETED).size();

        log.info("Database Verification:");
        log.info("  Total Jobs:         {}", totalJobs);
        log.info("  Completed Jobs:     {}", completedJobs);
        log.info("  Total Results:      {}", totalResults);
        log.info("  Completion Rate:    {:.1f}%", (completedJobs * 100.0 / totalJobs));
        log.info(SEPARATOR);
    }

    /**
     * Create a backtest request with varying parameters to ensure unique jobs.
     */
    private BacktestSubmissionRequest createRequest(int index) {
        Map<String, Object> params = new HashMap<>();

        // Vary parameters to create unique jobs
        params.put("shortPeriod", 5 + (index % 10));
        params.put("longPeriod", 20 + (index % 30));
        params.put("initialCapital", 10000.0);

        String symbol = "SYM" + (index % 50); // 50 different symbols

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
