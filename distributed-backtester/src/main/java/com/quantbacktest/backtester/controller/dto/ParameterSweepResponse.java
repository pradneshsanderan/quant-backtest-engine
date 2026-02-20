package com.quantbacktest.backtester.controller.dto;

import com.quantbacktest.backtester.domain.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for parameter sweep job submission.
 * Includes aggregated results and best performing configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParameterSweepResponse {

    private Long sweepJobId;
    private JobStatus status;
    private String message;
    private Integer totalJobs;
    private Integer completedJobs;
    private Integer failedJobs;

    // Best performing job details (populated when sweep completes)
    private BestJobResult bestResult;

    // Individual job IDs for tracking
    private List<Long> childJobIds;

    /**
     * Details of the best performing job in the sweep.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BestJobResult {
        private Long jobId;
        private String strategyName;
        private String parameters;
        private BigDecimal totalReturn;
        private BigDecimal cagr;
        private BigDecimal volatility;
        private BigDecimal sharpeRatio;
        private BigDecimal sortinoRatio;
        private BigDecimal maxDrawdown;
        private BigDecimal winRate;
        private BigDecimal optimizationMetricValue;
    }
}
