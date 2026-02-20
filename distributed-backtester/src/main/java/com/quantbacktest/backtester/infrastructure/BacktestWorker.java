package com.quantbacktest.backtester.infrastructure;

import com.quantbacktest.backtester.domain.BacktestJob;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.service.BacktestExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Background worker that polls Redis queue and processes backtest jobs.
 * Each worker runs in its own thread and continuously polls for jobs.
 */
@RequiredArgsConstructor
@Slf4j
public class BacktestWorker implements Runnable {

    private final QueueService queueService;
    private final BacktestJobRepository backtestJobRepository;
    private final BacktestExecutor backtestExecutor;
    private final String workerName;

    private volatile boolean running = true;

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
        try {
            // Fetch job from database
            Optional<BacktestJob> jobOptional = backtestJobRepository.findById(jobId);

            if (jobOptional.isEmpty()) {
                log.warn("{} - Job {} not found in database", workerName, jobId);
                return;
            }

            BacktestJob job = jobOptional.get();
            log.info("{} processing job {} - Strategy: {}, Symbol: {}",
                    workerName, job.getId(), job.getStrategyName(), job.getSymbol());

            // Execute the backtest
            backtestExecutor.executeBacktest(job);

        } catch (Exception e) {
            log.error("{} failed to process job {}: {}",
                    workerName, jobId, e.getMessage(), e);
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
