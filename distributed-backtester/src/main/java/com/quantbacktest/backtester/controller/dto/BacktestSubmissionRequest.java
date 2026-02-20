package com.quantbacktest.backtester.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Request DTO for submitting a new backtest job.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestSubmissionRequest {

    @NotBlank(message = "Strategy name is required")
    private String strategyName;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @NotNull(message = "Parameters are required")
    private Map<String, Object> parameters;

    @NotNull(message = "Initial capital is required")
    @Positive(message = "Initial capital must be positive")
    private BigDecimal initialCapital;
}
