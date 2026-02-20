package com.quantbacktest.backtester.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for submitting a parameter sweep job.
 * Allows testing multiple strategies and parameter combinations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParameterSweepRequest {

    @NotBlank(message = "Sweep name is required")
    private String name;

    private String description;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @NotNull(message = "Initial capital is required")
    @Positive(message = "Initial capital must be positive")
    private BigDecimal initialCapital;

    @NotEmpty(message = "At least one strategy configuration is required")
    private List<StrategyConfig> strategies;

    @NotBlank(message = "Optimization metric is required")
    private String optimizationMetric; // "sharpeRatio", "totalReturn", "sortinoRatio", etc.

    /**
     * Configuration for a single strategy with parameter combinations.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StrategyConfig {

        @NotBlank(message = "Strategy name is required")
        private String strategyName;

        @NotEmpty(message = "At least one parameter combination is required")
        private List<Map<String, Object>> parameterCombinations;
    }
}
