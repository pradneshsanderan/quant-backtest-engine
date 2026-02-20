package com.quantbacktest.backtester.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Entity representing the results of a completed backtest job.
 * Contains performance metrics and detailed results in JSON format.
 */
@Entity
@Table(name = "backtest_results", indexes = {
        @Index(name = "idx_job_id", columnList = "job_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_result_job"))
    private BacktestJob job;

    @Column(name = "total_return", precision = 10, scale = 4)
    private BigDecimal totalReturn;

    @Column(name = "cagr", precision = 10, scale = 4)
    private BigDecimal cagr;

    @Column(name = "volatility", precision = 10, scale = 4)
    private BigDecimal volatility;

    @Column(name = "sharpe_ratio", precision = 10, scale = 4)
    private BigDecimal sharpeRatio;

    @Column(name = "sortino_ratio", precision = 10, scale = 4)
    private BigDecimal sortinoRatio;

    @Column(name = "max_drawdown", precision = 10, scale = 4)
    private BigDecimal maxDrawdown;

    @Column(name = "win_rate", precision = 10, scale = 4)
    private BigDecimal winRate;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;
}
