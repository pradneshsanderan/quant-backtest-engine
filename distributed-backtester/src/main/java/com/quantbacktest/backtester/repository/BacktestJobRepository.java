package com.quantbacktest.backtester.repository;

import com.quantbacktest.backtester.domain.BacktestJob;
import com.quantbacktest.backtester.domain.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for BacktestJob entity.
 * Provides data access operations for backtest jobs.
 */
@Repository
public interface BacktestJobRepository extends JpaRepository<BacktestJob, Long> {

    /**
     * Find a job by its idempotency key.
     *
     * @param idempotencyKey the unique idempotency key
     * @return Optional containing the job if found
     */
    Optional<BacktestJob> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find and lock a job by ID for update (pessimistic write lock).
     * Prevents race conditions during status transitions.
     *
     * @param id the job ID
     * @return Optional containing the locked job if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM BacktestJob j WHERE j.id = :id")
    Optional<BacktestJob> findByIdForUpdate(@Param("id") Long id);

    /**
     * Find all jobs with a specific status.
     *
     * @param status the job status
     * @return list of jobs with the given status
     */
    List<BacktestJob> findByStatus(JobStatus status);

    /**
     * Check if a job exists with the given idempotency key.
     *
     * @param idempotencyKey the idempotency key to check
     * @return true if exists, false otherwise
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Find all jobs for a specific strategy.
     *
     * @param strategyName the strategy name
     * @return list of jobs for the given strategy
     */
    List<BacktestJob> findByStrategyName(String strategyName);

    /**
     * Find all jobs belonging to a parameter sweep.
     *
     * @param parentSweepJobId the parent sweep job ID
     * @return list of child jobs
     */
    List<BacktestJob> findByParentSweepJobId(Long parentSweepJobId);

    /**
     * Count completed jobs for a parameter sweep.
     *
     * @param parentSweepJobId the parent sweep job ID
     * @param status           the job status
     * @return count of jobs
     */
    long countByParentSweepJobIdAndStatus(Long parentSweepJobId, JobStatus status);
}
