package com.quantbacktest.backtester.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a backtest job in the system.
 * Tracks the lifecycle and configuration of a single backtest execution.
 */
@Entity
@Table(name = "backtest_jobs", uniqueConstraints = {
                @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key")
}, indexes = {
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_created_at", columnList = "created_at"),
                @Index(name = "idx_strategy_name", columnList = "strategy_name"),
                @Index(name = "idx_parent_sweep_job", columnList = "parent_sweep_job_id")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestJob {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Version
        @Column(name = "version")
        private Long version;

        @Column(name = "strategy_name", nullable = false, length = 255)
        private String strategyName;

        @Column(name = "symbol", nullable = false, length = 50)
        private String symbol;

        @Column(name = "start_date", nullable = false)
        private LocalDate startDate;

        @Column(name = "end_date", nullable = false)
        private LocalDate endDate;

        @Column(name = "parameters_json", columnDefinition = "TEXT")
        private String parametersJson;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 20)
        private JobStatus status;

        @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
        private String idempotencyKey;

        @Column(name = "parent_sweep_job_id")
        private Long parentSweepJobId;

        @Column(name = "retry_count", nullable = false)
        @Builder.Default
        private Integer retryCount = 0;

        @Column(name = "failure_reason", columnDefinition = "TEXT")
        private String failureReason;

        @CreatedDate
        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;

        @LastModifiedDate
        @Column(name = "updated_at", nullable = false)
        private LocalDateTime updatedAt;
}
