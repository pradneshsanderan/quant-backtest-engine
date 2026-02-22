package com.quantbacktest.backtester.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbacktest.backtester.domain.*;
import com.quantbacktest.backtester.infrastructure.QueueService;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.repository.BacktestResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private final MarketDataService marketDataService;
    private final StrategyFactory strategyFactory;
    private final ObjectMapper objectMapper;
    private final ParameterSweepService parameterSweepService;
    private final BacktestMetricsService metricsService;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void executeBacktest(BacktestJob job) {
        // Set MDC for structured logging
        MDC.put("jobId", String.valueOf(job.getId()));

        try {
            executeBacktestInternal(job);
        } finally {
            MDC.remove("jobId");
        }
    }

    /**
     * Internal execution method with proper locking and race condition prevention.
     */
    private void executeBacktestInternal(BacktestJob job) {
        if (job == null || job.getId() == null) {
            log.error("Invalid job: job or job ID is null");
            return;
        }

        long startTime = System.currentTimeMillis();

        // Log lifecycle start
        if (job.getRetryCount() == 0) {
            log.info("Started");
        } else {
            log.info("Retry {}", job.getRetryCount());
        }

        try {
            // CRITICAL: Acquire pessimistic lock to prevent race conditions
            // This prevents TOCTOU (Time-of-check-time-of-use) vulnerability
            BacktestJob lockedJob = backtestJobRepository.findByIdForUpdate(job.getId())
                    .orElseThrow(() -> new IllegalStateException("Job not found: " + job.getId()));

            // Race condition check: verify job is not already completed
            // This can happen if multiple workers dequeue the same job
            if (lockedJob.getStatus() == JobStatus.COMPLETED) {
                log.warn("Already COMPLETED. Skipping execution to prevent duplicate processing.");
                return;
            }

            // Also check if another worker is already processing it
            if (lockedJob.getStatus() == JobStatus.RUNNING) {
                log.warn("Already RUNNING by another worker. Skipping duplicate execution.");
                return;
            }

            // Update job status to RUNNING (within the locked transaction)
            lockedJob.setStatus(JobStatus.RUNNING);
            lockedJob.setUpdatedAt(LocalDateTime.now());
            backtestJobRepository.save(lockedJob);

            log.info("Status changed to RUNNING");

            // Execute backtest logic
            BacktestResult result = performBacktest(lockedJob, startTime);

            if (result == null) {
                throw new IllegalStateException("Backtest execution returned null result");
            }

            // Save result
            backtestResultRepository.save(result);

            // Calculate and log execution time
            long executionTimeMs = System.currentTimeMillis() - startTime;
            double executionTimeSec = executionTimeMs / 1000.0;

            log.info("Backtest result saved");

            // Mark job as COMPLETED
            lockedJob.setStatus(JobStatus.COMPLETED);
            lockedJob.setUpdatedAt(LocalDateTime.now());
            backtestJobRepository.save(lockedJob);

            log.info("Status changed to COMPLETED");
            log.info("Completed in {:.2f}s", executionTimeSec);

            // Record metrics
            metricsService.recordJobCompleted(executionTimeMs);

            // Update parent sweep job if this is part of a sweep
            if (lockedJob.getParentSweepJobId() != null) {
                try {
                    parameterSweepService.checkSweepProgress(lockedJob.getParentSweepJobId());
                } catch (Exception e) {
                    log.error("Failed to update sweep progress: {}", e.getMessage(), e);
                }
            }

        } catch (OptimisticLockingFailureException e) {
            log.warn(
                    "Concurrent modification detected (optimistic lock). Job may have been processed by another worker.");
            // Don't retry - another worker handled it
        } catch (IllegalStateException e) {
            log.error("Invalid state: {}", e.getMessage(), e);
            handleFailure(job, e);
        } catch (RuntimeException e) {
            log.error("Error during execution: {}", e.getMessage(), e);
            handleFailure(job, e);
        } catch (Exception e) {
            log.error("Unexpected error during execution: {}", e.getMessage(), e);
            handleFailure(job, e);
        }
    }

    /**
     * Execute backtest using the real backtesting engine.
     */
    private com.quantbacktest.backtester.domain.BacktestResult performBacktest(BacktestJob job, long startTime) {
        if (job == null) {
            throw new IllegalArgumentException("Job cannot be null");
        }

        log.info("Performing backtest - Strategy: {}, Symbol: {}, Period: {} to {}",
                job.getStrategyName(), job.getSymbol(),
                job.getStartDate(), job.getEndDate());

        try {
            // Load market data
            List<MarketData> marketData = marketDataService.loadMarketData(
                    job.getSymbol(), job.getStartDate(), job.getEndDate());

            if (marketData == null || marketData.isEmpty()) {
                throw new IllegalStateException("No market data available for the specified period");
            }

            // Parse parameters and get initial capital
            BigDecimal initialCapital = parseInitialCapital(job.getParametersJson());

            // Create strategy
            Strategy strategy = strategyFactory.createStrategy(
                    job.getStrategyName(), job.getParametersJson());

            if (strategy == null) {
                throw new IllegalStateException("Strategy factory returned null for: " + job.getStrategyName());
            }

            // Run backtest
            BacktestEngine engine = new BacktestEngine();
            BacktestEngine.BacktestConfig config = BacktestEngine.BacktestConfig.builder()
                    .strategy(strategy)
                    .marketData(marketData)
                    .initialCapital(initialCapital)
                    .build();

            BacktestEngine.BacktestResult engineResult = engine.runBacktest(config);

            if (engineResult == null) {
                throw new IllegalStateException("Backtest engine returned null result");
            }

            // Convert trades to JSON
            String tradesJson = objectMapper.writeValueAsString(engineResult.getTrades());

            // Calculate execution time
            long executionTimeMs = System.currentTimeMillis() - startTime;

            // Create result entity with all performance metrics
            return com.quantbacktest.backtester.domain.BacktestResult.builder()
                    .job(job)
                    .totalReturn(engineResult.getTotalReturn())
                    .cagr(engineResult.getCagr())
                    .volatility(engineResult.getVolatility())
                    .sharpeRatio(engineResult.getSharpeRatio())
                    .sortinoRatio(engineResult.getSortinoRatio())
                    .maxDrawdown(engineResult.getMaxDrawdown())
                    .winRate(engineResult.getWinRate())
                    .executionTimeMs(executionTimeMs)
                    .resultJson(tradesJson)
                    .build();

        } catch (IllegalStateException | IllegalArgumentException e) {
            // Re-throw validation errors
            throw e;
        } catch (Exception e) {
            log.error("Backtest execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("Backtest execution failed", e);
        }
    }

    /**
     * Parse initial capital from parameters JSON.
     */
    private BigDecimal parseInitialCapital(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) {
            log.debug("No parameters JSON provided, using default initial capital");
            return new BigDecimal("10000.00");
        }

        try {
            var params = objectMapper.readTree(parametersJson);
            if (params != null && params.has("initialCapital") && !params.get("initialCapital").isNull()) {
                String capitalStr = params.get("initialCapital").asText();
                BigDecimal capital = new BigDecimal(capitalStr);

                if (capital.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("Invalid initial capital: {}. Using default.", capitalStr);
                    return new BigDecimal("10000.00");
                }

                return capital;
            }
            return new BigDecimal("10000.00"); // Default
        } catch (Exception e) {
            log.warn("Failed to parse initialCapital from parameters, using default: {}", e.getMessage());
            return new BigDecimal("10000.00");
        }
    }

    /**
     * Handle job failure with retry logic.
     * Uses separate transaction to ensure failure handling persists even if parent
     * transaction rolls back.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    private void handleFailure(BacktestJob job, Exception error) {
        if (job == null || job.getId() == null) {
            log.error("Cannot handle failure for null job");
            return;
        }

        // Set MDC for logging if not already set
        String jobIdStr = String.valueOf(job.getId());
        if (MDC.get("jobId") == null) {
            MDC.put("jobId", jobIdStr);
        }

        try {
            String errorMessage = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            // Truncate error message to prevent database field overflow
            if (errorMessage.length() > 1000) {
                errorMessage = errorMessage.substring(0, 997) + "...";
            }

            // Reload job with lock to prevent concurrent modifications
            BacktestJob lockedJob = backtestJobRepository.findByIdForUpdate(job.getId())
                    .orElse(job);

            lockedJob.setRetryCount(lockedJob.getRetryCount() + 1);
            lockedJob.setFailureReason(errorMessage);
            lockedJob.setUpdatedAt(LocalDateTime.now());

            if (lockedJob.getRetryCount() < MAX_RETRY_COUNT) {
                log.warn("Failed (attempt {}/{}): {}. Requeuing for retry...",
                        lockedJob.getRetryCount(), MAX_RETRY_COUNT, errorMessage);

                lockedJob.setStatus(JobStatus.QUEUED);
                backtestJobRepository.save(lockedJob);

                // Requeue the job
                try {
                    queueService.push(lockedJob.getId());
                    log.info("Status changed to QUEUED");
                    log.info("Requeued for retry attempt {}", lockedJob.getRetryCount() + 1);
                } catch (Exception queueEx) {
                    log.error("Failed to requeue job: {}", queueEx.getMessage(), queueEx);
                    // Mark as FAILED if we can't requeue
                    lockedJob.setStatus(JobStatus.FAILED);
                    backtestJobRepository.save(lockedJob);
                }

                // Record retry metric
                try {
                    metricsService.recordJobRetried();
                } catch (Exception metricsEx) {
                    log.warn("Failed to record retry metric: {}", metricsEx.getMessage());
                }
            } else {
                log.error("Failed permanently after {} attempts: {}",
                        lockedJob.getRetryCount(), errorMessage);
                log.error("DEAD LETTER QUEUE: Job marked as FAILED and will not be retried");

                lockedJob.setStatus(JobStatus.FAILED);
                backtestJobRepository.save(lockedJob);

                log.info("Status changed to FAILED");

                // Record failure metric
                try {
                    metricsService.recordJobFailed();
                } catch (Exception metricsEx) {
                    log.warn("Failed to record failure metric: {}", metricsEx.getMessage());
                }

                // Update parent sweep job if this is part of a sweep
                if (lockedJob.getParentSweepJobId() != null) {
                    try {
                        parameterSweepService.checkSweepProgress(lockedJob.getParentSweepJobId());
                    } catch (Exception e) {
                        log.error("Failed to update sweep progress: {}", e.getMessage(), e);
                    }
                }
            }
        } finally {
            if (MDC.get("jobId") != null) {
                MDC.remove("jobId");
            }
        }
    }
}
