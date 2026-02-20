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

    @Override
    @Transactional
    public void executeBacktest(BacktestJob job) {
        log.info("Starting execution of backtest job {}", job.getId());

        try {
            // Race condition check: verify job is not already completed
            // This can happen if multiple workers dequeue the same job
            if (job.getStatus() == JobStatus.COMPLETED) {
                log.warn("Job {} is already COMPLETED. Skipping execution to prevent duplicate processing.",
                        job.getId());
                return;
            }

            // Also check if another worker is already processing it
            if (job.getStatus() == JobStatus.RUNNING) {
                log.warn("Job {} is already RUNNING by another worker. Skipping duplicate execution.",
                        job.getId());
                return;
            }

            // Update job status to RUNNING
            job.setStatus(JobStatus.RUNNING);
            job.setUpdatedAt(LocalDateTime.now());
            backtestJobRepository.save(job);

            log.info("Job {} marked as RUNNING", job.getId());

            // Execute backtest logic
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
     * Execute backtest using the real backtesting engine.
     */
    private com.quantbacktest.backtester.domain.BacktestResult performBacktest(BacktestJob job) {
        log.info("Performing backtest for job {} - Strategy: {}, Symbol: {}, Period: {} to {}",
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

            // Create result entity
            return com.quantbacktest.backtester.domain.BacktestResult.builder()
                    .job(job)
                    .totalReturn(engineResult.getTotalReturn())
                    .sharpeRatio(engineResult.getSharpeRatio())
                    .maxDrawdown(engineResult.getMaxDrawdown())
                    .winRate(engineResult.getWinRate())
                    .resultJson(tradesJson)
                    .build();

        } catch (Exception e) {
            log.error("Backtest execution failed for job {}", job.getId(), e);
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
