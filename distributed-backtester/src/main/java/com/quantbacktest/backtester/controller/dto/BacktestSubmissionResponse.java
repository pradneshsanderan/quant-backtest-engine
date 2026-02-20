package com.quantbacktest.backtester.controller.dto;

import com.quantbacktest.backtester.domain.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for backtest job submission.
 * Includes result data if job is already completed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestSubmissionResponse {

    private Long jobId;
    private JobStatus status;
    private String message;
    private Boolean isExisting;

    // Result fields (populated only if status is COMPLETED)
    private BigDecimal totalReturn;
    private BigDecimal cagr;
    private BigDecimal volatility;
    private BigDecimal sharpeRatio;
    private BigDecimal sortinoRatio;
    private BigDecimal maxDrawdown;
    private BigDecimal winRate;
}
