package com.quantbacktest.backtester.domain;

/**
 * Status enum for backtest job lifecycle.
 */
public enum JobStatus {
    SUBMITTED,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}
