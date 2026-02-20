package com.quantbacktest.backtester.service;

import com.quantbacktest.backtester.controller.dto.BacktestSubmissionRequest;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionResponse;

/**
 * Service interface for backtest job operations.
 */
public interface BacktestService {

    /**
     * Submit a new backtest job or return existing job if duplicate.
     *
     * @param request the backtest submission request
     * @return the submission response with job ID and status
     */
    BacktestSubmissionResponse submitBacktest(BacktestSubmissionRequest request);
}
