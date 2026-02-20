package com.quantbacktest.backtester.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for tracking backtest execution metrics.
 * Exposes metrics via Spring Boot Actuator for monitoring.
 */
@Service
@Slf4j
public class BacktestMetricsService {

    private final Counter jobsSubmittedCounter;
    private final Counter jobsCompletedCounter;
    private final Counter jobsFailedCounter;
    private final Counter jobsRetriedCounter;
    private final Timer executionTimer;

    public BacktestMetricsService(MeterRegistry meterRegistry) {
        this.jobsSubmittedCounter = Counter.builder("backtest.jobs.submitted")
                .description("Total number of backtest jobs submitted")
                .register(meterRegistry);

        this.jobsCompletedCounter = Counter.builder("backtest.jobs.completed")
                .description("Total number of backtest jobs completed successfully")
                .register(meterRegistry);

        this.jobsFailedCounter = Counter.builder("backtest.jobs.failed")
                .description("Total number of backtest jobs failed permanently")
                .register(meterRegistry);

        this.jobsRetriedCounter = Counter.builder("backtest.jobs.retried")
                .description("Total number of backtest job retry attempts")
                .register(meterRegistry);

        this.executionTimer = Timer.builder("backtest.execution.time")
                .description("Backtest job execution time")
                .register(meterRegistry);

        log.info("BacktestMetricsService initialized with Micrometer metrics");
    }

    /**
     * Record a job submission.
     */
    public void recordJobSubmitted() {
        jobsSubmittedCounter.increment();
    }

    /**
     * Record a successful job completion with execution time.
     */
    public void recordJobCompleted(long executionTimeMs) {
        jobsCompletedCounter.increment();
        executionTimer.record(executionTimeMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Record a permanent job failure.
     */
    public void recordJobFailed() {
        jobsFailedCounter.increment();
    }

    /**
     * Record a job retry attempt.
     */
    public void recordJobRetried() {
        jobsRetriedCounter.increment();
    }

    /**
     * Get current metrics summary (for logging purposes).
     */
    public String getMetricsSummary() {
        return String.format("Metrics: Submitted=%d, Completed=%d, Failed=%d, Retried=%d, AvgExecTime=%.2fs",
                (long) jobsSubmittedCounter.count(),
                (long) jobsCompletedCounter.count(),
                (long) jobsFailedCounter.count(),
                (long) jobsRetriedCounter.count(),
                executionTimer.mean(TimeUnit.SECONDS));
    }
}
