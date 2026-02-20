package com.quantbacktest.backtester.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbacktest.backtester.domain.*;
import com.quantbacktest.backtester.infrastructure.QueueService;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.repository.BacktestResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
    @Transactional
    public void executeBacktest(BacktestJob job) {
        long startTime = System.currentTimeMillis();

        // Log lifecycle start
        if (job.getRetryCount() == 0) {
            log.info("[JobId={}] Started", job.getId());
        } else {
            log.info("[JobId={}] Retry {}", job.getId(), job.getRetryCount());
        }

        try {
            // Race condition check: verify job is not already completed
            // This can happen if multiple workers dequeue the same job
            if (job.getStatus() == JobStatus.COMPLETED) {
                log.warn("[JobId={}] Already COMPLETED. Skipping execution to prevent duplicate processing.",
                        job.getId());
                return;
            }

            // Also check if another worker is already processing it
            if (job.getStatus() == JobStatus.RUNNING) {
                log.warn("[JobId={}] Already RUNNING by another worker. Skipping duplicate execution.",
                        job.getId());
                return;
            }

            // Update job status to RUNNING
            job.setStatus(JobStatus.RUNNING);
            job.setUpdatedAt(LocalDateTime.now());
            backtestJobRepository.save(job);

            log.info("[JobId={}] Status changed to RUNNING", job.getId());

            // Execute backtest logic
            BacktestResult result = performBacktest(job, startTime);

            // Save result
            backtestResultRepository.save(result);

            // Calculate and log execution time
            long executionTimeMs = System.currentTimeMillis() - startTime;
            double executionTimeSec = executionTimeMs / 1000.0;

            log.info("[JobId={}] Backtest result saved", job.getId());

            // Mark job as COMPLETED
            job.setStatus(JobStatus.COMPLETED);
            job.setUpdatedAt(LocalDateTime.now());
            backtestJobRepository.save(job);

            log.info("[JobId={}] Status changed to COMPLETED", job.getId());
            log.info("[JobId={}] Completed in {:.2f}s", job.getId(), executionTimeSec);

            // Record metrics
            metricsService.recordJobCompleted(executionTimeMs);

            // Update parent sweep job if this is part of a sweep
            if (job.getParentSweepJobId() != null) {
                try {
                    parameterSweepService.checkSweepProgress(job.getParentSweepJobId());
                } catch (Exception e) {
                    log.error("Failed to update sweep progress for job {}: {}",
                            job.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("[JobId={}] Error during execution: {}", job.getId(), e.getMessage(), e);
            handleFailure(job, e);
        }
    }

    /**
     * Execute backtest using the real backtesting engine.
     */
    private com.quantbacktest.backtester.domain.BacktestResult performBacktest(BacktestJob job, long startTime) {
        log.info("[JobId={}] Performing backtest - Strategy: {}, Symbol: {}, Period: {} to {}",
                job.getId(), job.getStrategyName(), job.getSymbol(),
                job.getStartDate(), job.getEndDate());

        try {
            // Load market data
            List<MarketData> marketData = marketDataService.loadMarketData(
                    job.getSymbol(), job.getStartDate(), job.getEndDate());

            if (marketData.isEmpty()) {
                throw new RuntimeException("No market data available for the specified period");
            }

            // Parse parameters and get initial capital
            BigDecimal initialCapital = parseInitialCapital(job.getParametersJson());

            // Create strategy
            Strategy strategy = strategyFactory.createStrategy(
                    job.getStrategyName(), job.getParametersJson());

            // Run backtest
            BacktestEngine engine = new BacktestEngine();
            BacktestEngine.BacktestConfig config = BacktestEngine.BacktestConfig.builder()
                    .strategy(strategy)
                    .marketData(marketData)
                    .initialCapital(initialCapital)
                    .build();

            BacktestEngine.BacktestResult engineResult = engine.runBacktest(config);

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

        } catch (Exception e) {
            log.error("[JobId={}] Backtest execution failed: {}", job.getId(), e.getMessage(), e);
            throw new RuntimeException("Backtest execution failed", e);
        }
    }

    /**
     * Parse initial capital from parameters JSON.
     */
    private BigDecimal parseInitialCapital(String parametersJson) {
        try {
            var params = objectMapper.readTree(parametersJson);
            if (params.has("initialCapital")) {
                return new BigDecimal(params.get("initialCapital").asText());
            }
            return new BigDecimal("10000.00"); // Default
        } catch (Exception e) {
            log.warn("Failed to parse initialCapital from parameters, using default", e);
            return new BigDecimal("10000.00");
        }
    }

    /**
     * Handle job failure with retry logic.
     */
    @Transactional
    private void handleFailure(BacktestJob job, Exception error) {
        String errorMessage = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();

        job.setRetryCount(job.getRetryCount() + 1);
        job.setFailureReason(errorMessage);
        job.setUpdatedAt(LocalDateTime.now());

        if (job.getRetryCount() < MAX_RETRY_COUNT) {
            log.warn("[JobId={}] Failed (attempt {}/{}): {}. Requeuing for retry...",
                    job.getId(), job.getRetryCount(), MAX_RETRY_COUNT, errorMessage);

            job.setStatus(JobStatus.QUEUED);
            backtestJobRepository.save(job);

            // Requeue the job
            queueService.push(job.getId());

            log.info("[JobId={}] Status changed to QUEUED", job.getId());
            log.info("[JobId={}] Requeued for retry attempt {}", job.getId(), job.getRetryCount() + 1);

            // Record retry metric
            metricsService.recordJobRetried();
        } else {
            log.error("[JobId={}] Failed permanently after {} attempts: {}",
                    job.getId(), job.getRetryCount(), errorMessage);
            log.error("[JobId={}] DEAD LETTER QUEUE: Job marked as FAILED and will not be retried",
                    job.getId());

            job.setStatus(JobStatus.FAILED);
            backtestJobRepository.save(job);

            log.info("[JobId={}] Status changed to FAILED", job.getId());

            // Record failure metric
            metricsService.recordJobFailed();

            // Update parent sweep job if this is part of a sweep
            if (job.getParentSweepJobId() != null) {
                try {
                    parameterSweepService.checkSweepProgress(job.getParentSweepJobId());
                } catch (Exception e) {
                    log.error("Failed to update sweep progress for failed job {}: {}",
                            job.getId(), e.getMessage(), e);
                }
            }
        }
    }
}
