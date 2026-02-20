package com.quantbacktest.backtester.controller.dto;

import com.quantbacktest.backtester.domain.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for backtest job submission.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestSubmissionResponse {

    private Long jobId;
    private JobStatus status;
    private String message;
}
