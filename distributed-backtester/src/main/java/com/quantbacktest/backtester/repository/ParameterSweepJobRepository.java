package com.quantbacktest.backtester.repository;

import com.quantbacktest.backtester.domain.ParameterSweepJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for parameter sweep jobs.
 */
@Repository
public interface ParameterSweepJobRepository extends JpaRepository<ParameterSweepJob, Long> {
}
