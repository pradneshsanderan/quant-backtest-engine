package com.quantbacktest.backtester.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity representing a parameter sweep job.
 * A sweep job coordinates multiple child BacktestJob executions
 * with different parameter combinations.
 */
@Entity
@Table(name = "parameter_sweep_jobs", indexes = {
        @Index(name = "idx_sweep_status", columnList = "status"),
        @Index(name = "idx_sweep_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParameterSweepJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "total_jobs", nullable = false)
    private Integer totalJobs;

    @Column(name = "completed_jobs", nullable = false)
    @Builder.Default
    private Integer completedJobs = 0;

    @Column(name = "failed_jobs", nullable = false)
    @Builder.Default
    private Integer failedJobs = 0;

    @Column(name = "best_job_id")
    private Long bestJobId;

    @Column(name = "best_metric_value", precision = 12, scale = 4)
    private java.math.BigDecimal bestMetricValue;

    @Column(name = "optimization_metric", length = 50)
    private String optimizationMetric; // e.g., "sharpeRatio", "totalReturn", "sortinoRatio"

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
