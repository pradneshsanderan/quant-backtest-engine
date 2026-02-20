package com.quantbacktest.backtester.service;

import com.quantbacktest.backtester.domain.BacktestJob;

/**
 * Service interface for executing backtest jobs.
 */
public interface BacktestExecutor {

    /**
     * Execute a backtest job.
     * Processes the job, generates results, and updates job status.
     *
     * @param job the backtest job to execute
     */
    void executeBacktest(BacktestJob job);
}
