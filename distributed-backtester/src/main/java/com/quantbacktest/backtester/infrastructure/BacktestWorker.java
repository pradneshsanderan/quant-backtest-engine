package com.quantbacktest.backtester.infrastructure;

import com.quantbacktest.backtester.domain.BacktestJob;
import com.quantbacktest.backtester.domain.JobStatus;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.service.BacktestExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Optional;

/**
 * Background worker that polls Redis queue and processes backtest jobs.
 * Each worker runs in its own thread and continuously polls for jobs.
 * Implements exponential backoff for retried jobs.
 */
@RequiredArgsConstructor
@Slf4j
public class BacktestWorker implements Runnable {

    private final QueueService queueService;
    private final BacktestJobRepository backtestJobRepository;
    private final BacktestExecutor backtestExecutor;
    private final String workerName;

    private volatile boolean running = true;

    // Exponential backoff delays in seconds: 1, 3, 5
    private static final long[] BACKOFF_DELAYS = { 1000, 3000, 5000 };

    @Override
    public void run() {
        log.info("{} started and polling queue", workerName);

        while (running) {
            try {
                // Poll queue for job ID
                Long jobId = queueService.pop();

                if (jobId != null) {
                    log.info("{} received job ID: {}", workerName, jobId);
                    processJob(jobId);
                }

            } catch (Exception e) {
                log.error("{} encountered error while polling queue: {}",
                        workerName, e.getMessage(), e);

                // Brief pause before retrying to avoid tight loop on persistent errors
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("{} interrupted during error recovery", workerName);
                    break;
                }
            }
        }

        log.info("{} stopped", workerName);
    }

    /**
     * Process a single job fetched from the queue.
     */
    private void processJob(Long jobId) {
        if (jobId == null) {
            log.warn("{} - Received null job ID", workerName);
            return;
        }

        // Set MDC for structured logging
        MDC.put("jobId", String.valueOf(jobId));
        MDC.put("worker", workerName);

        try {
            // Fetch job from database
            Optional<BacktestJob> jobOptional = backtestJobRepository.findById(jobId);

            if (jobOptional.isEmpty()) {
                log.warn("Job not found in database");
                return;
            }

            BacktestJob job = jobOptional.get();

            // Idempotency check: verify job should be processed
            if (job.getStatus() == JobStatus.COMPLETED) {
                log.warn("Job is already COMPLETED. Skipping duplicate processing.");
                return;
            }

            if (job.getStatus() == JobStatus.RUNNING) {
                log.warn("Job is already RUNNING. Another worker may be processing it.");
                return;
            }

            if (job.getStatus() == JobStatus.FAILED) {
                log.info("Job is marked FAILED. This may be a retry attempt.");
            }

            // Apply exponential backoff if this is a retry
            if (job.getRetryCount() > 0) {
                applyExponentialBackoff(job);
            }

            log.info("Processing - Strategy: {}, Symbol: {}, Status: {}, RetryCount: {}",
                    job.getStrategyName(), job.getSymbol(),
                    job.getStatus(), job.getRetryCount());

            // Execute the backtest
            backtestExecutor.executeBacktest(job);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Processing interrupted");
            throw new RuntimeException("Job processing interrupted", e);
        } catch (Exception e) {
            log.error("Failed to process job: {}", e.getMessage(), e);
            // Exception is logged - executor handles failure logic internally
        } finally {
            MDC.remove("jobId");
            MDC.remove("worker");
        }
    }

    /**
     * Apply exponential backoff delay before processing a retried job.
     * Delays: 1st retry = 1s, 2nd retry = 3s, 3rd retry = 5s
     */
    private void applyExponentialBackoff(BacktestJob job) throws InterruptedException {
        if (job == null) {
            return;
        }

        int retryIndex = job.getRetryCount() - 1;
        if (retryIndex >= 0 && retryIndex < BACKOFF_DELAYS.length) {
            long delayMs = BACKOFF_DELAYS[retryIndex];
            log.info("Applying exponential backoff: {}ms before retry {}",
                    delayMs, job.getRetryCount());

            Thread.sleep(delayMs);
        }
    }

    /**
     * Gracefully stop the worker.
     */
    public void stop() {
        log.info("Stopping {}", workerName);
        running = false;
    }
}
