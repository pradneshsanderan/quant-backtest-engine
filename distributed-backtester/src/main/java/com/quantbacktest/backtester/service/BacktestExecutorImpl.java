package com.quantbacktest.backtester.service;

import com.quantbacktest.backtester.domain.BacktestJob;
import com.quantbacktest.backtester.domain.BacktestResult;
import com.quantbacktest.backtester.domain.JobStatus;
import com.quantbacktest.backtester.infrastructure.QueueService;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.repository.BacktestResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Implementation of BacktestExecutor for executing backtest jobs.
 * Handles job execution with proper transaction management and failure
 * handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestExecutorImpl implements BacktestExecutor {

    private static final int MAX_RETRY_COUNT = 3;

    private final BacktestJobRepository backtestJobRepository;
    private final BacktestResultRepository backtestResultRepository;
    private final QueueService queueService;

    @Override
    @Transactional
    public void executeBacktest(BacktestJob job) {
        log.info("Starting execution of backtest job {}", job.getId());

        try {
            // Update job status to RUNNING
            job.setStatus(JobStatus.RUNNING);
            job.setUpdatedAt(LocalDateTime.now());
            backtestJobRepository.save(job);

            log.info("Job {} marked as RUNNING", job.getId());

            // Execute backtest logic (placeholder)
            BacktestResult result = performBacktest(job);

            // Save result
            backtestResultRepository.save(result);
            log.info("Backtest result saved for job {}", job.getId());

            // Mark job as COMPLETED
            job.setStatus(JobStatus.COMPLETED);
            job.setUpdatedAt(LocalDateTime.now());
            backtestJobRepository.save(job);

            log.info("Job {} completed successfully", job.getId());

        } catch (Exception e) {
            log.error("Error executing backtest job {}: {}", job.getId(), e.getMessage(), e);
            handleFailure(job, e);
        }
    }

    /**
     * Placeholder backtest logic.
     * TODO: Implement actual backtesting algorithm.
     */
    private BacktestResult performBacktest(BacktestJob job) {
        log.info("Performing backtest for job {} - Strategy: {}, Symbol: {}, Period: {} to {}",
                job.getId(), job.getStrategyName(), job.getSymbol(),
                job.getStartDate(), job.getEndDate());

        // Simulate processing time
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Backtest interrupted", e);
        }

        // Generate placeholder results
        return BacktestResult.builder()
                .job(job)
                .totalReturn(new BigDecimal("15.25"))
                .sharpeRatio(new BigDecimal("1.45"))
                .maxDrawdown(new BigDecimal("-8.30"))
                .winRate(new BigDecimal("0.62"))
                .resultJson("{\"trades\": 150, \"placeholder\": true}")
                .build();
    }

    /**
     * Handle job failure with retry logic.
     */
    @Transactional
    private void handleFailure(BacktestJob job, Exception error) {
        job.setRetryCount(job.getRetryCount() + 1);
        job.setUpdatedAt(LocalDateTime.now());

        if (job.getRetryCount() < MAX_RETRY_COUNT) {
            log.warn("Job {} failed (attempt {}/{}). Requeuing...",
                    job.getId(), job.getRetryCount(), MAX_RETRY_COUNT);

            job.setStatus(JobStatus.QUEUED);
            backtestJobRepository.save(job);

            // Requeue the job
            queueService.push(job.getId());

            log.info("Job {} requeued for retry", job.getId());
        } else {
            log.error("Job {} failed after {} attempts. Marking as FAILED",
                    job.getId(), job.getRetryCount());

            job.setStatus(JobStatus.FAILED);
            backtestJobRepository.save(job);
        }
    }
}
