package com.quantbacktest.backtester.repository;

import com.quantbacktest.backtester.domain.BacktestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for BacktestResult entity.
 * Provides data access operations for backtest results.
 */
@Repository
public interface BacktestResultRepository extends JpaRepository<BacktestResult, Long> {

    /**
     * Find result by job ID.
     *
     * @param jobId the job ID
     * @return Optional containing the result if found
     */
    Optional<BacktestResult> findByJobId(Long jobId);

    /**
     * Find all results for a specific job.
     *
     * @param jobId the job ID
     * @return list of results for the given job
     */
    List<BacktestResult> findAllByJobId(Long jobId);

    /**
     * Check if a result exists for a given job.
     *
     * @param jobId the job ID
     * @return true if result exists, false otherwise
     */
    boolean existsByJobId(Long jobId);
}
