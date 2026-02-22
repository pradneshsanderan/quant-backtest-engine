package com.quantbacktest.backtester.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbacktest.backtester.controller.dto.ParameterSweepRequest;
import com.quantbacktest.backtester.controller.dto.ParameterSweepResponse;
import com.quantbacktest.backtester.domain.*;
import com.quantbacktest.backtester.infrastructure.QueueService;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.repository.BacktestResultRepository;
import com.quantbacktest.backtester.repository.ParameterSweepJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing parameter sweep jobs.
 * Handles submission, tracking, and aggregation of multiple backtest jobs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParameterSweepService {

    private final ParameterSweepJobRepository sweepJobRepository;
    private final BacktestJobRepository backtestJobRepository;
    private final BacktestResultRepository backtestResultRepository;
    private final QueueService queueService;
    private final ObjectMapper objectMapper;

    /**
     * Submit a parameter sweep job.
     * Creates parent sweep job and multiple child backtest jobs.
     */
    @Transactional
    public ParameterSweepResponse submitSweep(ParameterSweepRequest request) {
        log.info("Submitting parameter sweep: {}", request.getName());

        // Calculate total number of jobs
        int totalJobs = request.getStrategies().stream()
                .mapToInt(s -> s.getParameterCombinations().size())
                .sum();

        // Create parent sweep job
        ParameterSweepJob sweepJob = ParameterSweepJob.builder()
                .name(request.getName())
                .description(request.getDescription())
                .status(JobStatus.QUEUED)
                .totalJobs(totalJobs)
                .completedJobs(0)
                .failedJobs(0)
                .optimizationMetric(request.getOptimizationMetric())
                .build();

        sweepJob = sweepJobRepository.save(sweepJob);
        log.info("Created sweep job {} with {} total child jobs", sweepJob.getId(), totalJobs);

        // Create and queue child jobs
        List<Long> childJobIds = new ArrayList<>();

        for (ParameterSweepRequest.StrategyConfig strategyConfig : request.getStrategies()) {
            for (Map<String, Object> params : strategyConfig.getParameterCombinations()) {
                try {
                    // Add initial capital to parameters
                    params.put("initialCapital", request.getInitialCapital().toString());

                    String parametersJson = objectMapper.writeValueAsString(params);
                    String idempotencyKey = generateIdempotencyKey(
                            sweepJob.getId(),
                            strategyConfig.getStrategyName(),
                            request.getSymbol(),
                            request.getStartDate().toString(),
                            request.getEndDate().toString(),
                            parametersJson);

                    // Create child backtest job
                    BacktestJob childJob = BacktestJob.builder()
                            .strategyName(strategyConfig.getStrategyName())
                            .symbol(request.getSymbol())
                            .startDate(request.getStartDate())
                            .endDate(request.getEndDate())
                            .parametersJson(parametersJson)
                            .status(JobStatus.QUEUED)
                            .idempotencyKey(idempotencyKey)
                            .parentSweepJobId(sweepJob.getId())
                            .retryCount(0)
                            .build();

                    childJob = backtestJobRepository.save(childJob);
                    childJobIds.add(childJob.getId());

                    // Queue child job
                    queueService.push(childJob.getId());

                    log.debug("Created and queued child job {} for sweep {}", childJob.getId(), sweepJob.getId());

                } catch (Exception e) {
                    log.error("Failed to create child job for sweep {}: {}", sweepJob.getId(), e.getMessage(), e);
                }
            }
        }

        log.info("Sweep job {} submitted with {} child jobs queued", sweepJob.getId(), childJobIds.size());

        return ParameterSweepResponse.builder()
                .sweepJobId(sweepJob.getId())
                .status(sweepJob.getStatus())
                .message("Parameter sweep submitted successfully")
                .totalJobs(totalJobs)
                .completedJobs(0)
                .failedJobs(0)
                .childJobIds(childJobIds)
                .build();
    }

    /**
     * Check if sweep is complete and update best result.
     * Called after each child job completes.
     */
    @Transactional
    public void checkSweepProgress(Long sweepJobId) {
        if (sweepJobId == null) {
            log.warn("Sweep job ID is null");
            return;
        }

        Optional<ParameterSweepJob> sweepOptional = sweepJobRepository.findById(sweepJobId);
        if (sweepOptional.isEmpty()) {
            log.warn("Sweep job {} not found", sweepJobId);
            return;
        }

        ParameterSweepJob sweep = sweepOptional.get();

        // Count completed and failed jobs
        long completedCount = backtestJobRepository.countByParentSweepJobIdAndStatus(
                sweepJobId, JobStatus.COMPLETED);
        long failedCount = backtestJobRepository.countByParentSweepJobIdAndStatus(
                sweepJobId, JobStatus.FAILED);

        sweep.setCompletedJobs((int) completedCount);
        sweep.setFailedJobs((int) failedCount);

        // Check if sweep is complete
        int totalProcessed = (int) (completedCount + failedCount);
        if (totalProcessed >= sweep.getTotalJobs()) {
            sweep.setStatus(JobStatus.COMPLETED);
            sweep.setCompletedAt(LocalDateTime.now());

            // Find best result
            updateBestResult(sweep);

            log.info("Sweep job {} completed. Total: {}, Completed: {}, Failed: {}",
                    sweepJobId, sweep.getTotalJobs(), completedCount, failedCount);
        } else {
            sweep.setStatus(JobStatus.RUNNING);
        }

        sweepJobRepository.save(sweep);
    }

    /**
     * Find and update the best performing job in the sweep.
     * Uses batch query to avoid N+1 problem.
     */
    private void updateBestResult(ParameterSweepJob sweep) {
        if (sweep == null || sweep.getId() == null) {
            log.warn("Invalid sweep job");
            return;
        }

        List<BacktestJob> completedJobs = backtestJobRepository.findByParentSweepJobId(sweep.getId())
                .stream()
                .filter(job -> job.getStatus() == JobStatus.COMPLETED)
                .toList();

        if (completedJobs.isEmpty()) {
            log.warn("No completed jobs found for sweep {}", sweep.getId());
            return;
        }

        // OPTIMIZATION: Batch load all results to avoid N+1 query
        List<Long> jobIds = completedJobs.stream()
                .map(BacktestJob::getId)
                .toList();

        List<BacktestResult> results = backtestResultRepository.findByJobIdIn(jobIds);

        // Create a map for O(1) lookup
        Map<Long, BacktestResult> resultsByJobId = results.stream()
                .collect(java.util.stream.Collectors.toMap(
                        r -> r.getJob().getId(),
                        r -> r));

        BacktestJob bestJob = null;
        BigDecimal bestMetricValue = null;

        for (BacktestJob job : completedJobs) {
            BacktestResult result = resultsByJobId.get(job.getId());
            if (result == null) {
                log.debug("No result found for completed job {}", job.getId());
                continue;
            }

            BigDecimal metricValue = getMetricValue(result, sweep.getOptimizationMetric());

            if (metricValue != null && (bestMetricValue == null || metricValue.compareTo(bestMetricValue) > 0)) {
                bestMetricValue = metricValue;
                bestJob = job;
            }
        }

        if (bestJob != null) {
            sweep.setBestJobId(bestJob.getId());
            sweep.setBestMetricValue(bestMetricValue);
            log.info("Best job for sweep {} is job {} with {} = {}",
                    sweep.getId(), bestJob.getId(), sweep.getOptimizationMetric(), bestMetricValue);
        } else {
            log.warn("Could not determine best job for sweep {}", sweep.getId());
        }
    }

    /**
     * Get the specified metric value from a result.
     */
    private BigDecimal getMetricValue(BacktestResult result, String metricName) {
        if (result == null || metricName == null) {
            return null;
        }

        return switch (metricName.toLowerCase()) {
            case "totalreturn" -> result.getTotalReturn();
            case "sharperatio" -> result.getSharpeRatio();
            case "sortinoratio" -> result.getSortinoRatio();
            case "cagr" -> result.getCagr();
            case "winrate" -> result.getWinRate();
            case "maxdrawdown" -> result.getMaxDrawdown() != null ? result.getMaxDrawdown().negate() : null; // Negate
                                                                                                             // because
                                                                                                             // lower is
                                                                                                             // better
            default -> result.getSharpeRatio(); // Default to Sharpe ratio
        };
    }

    /**
     * Get sweep job status and results.
     */
    @Transactional(readOnly = true)
    public ParameterSweepResponse getSweepStatus(Long sweepJobId) {
        Optional<ParameterSweepJob> sweepOptional = sweepJobRepository.findById(sweepJobId);
        if (sweepOptional.isEmpty()) {
            throw new RuntimeException("Sweep job not found: " + sweepJobId);
        }

        ParameterSweepJob sweep = sweepOptional.get();

        ParameterSweepResponse.ParameterSweepResponseBuilder responseBuilder = ParameterSweepResponse.builder()
                .sweepJobId(sweep.getId())
                .status(sweep.getStatus())
                .totalJobs(sweep.getTotalJobs())
                .completedJobs(sweep.getCompletedJobs())
                .failedJobs(sweep.getFailedJobs())
                .message("Sweep job status retrieved");

        // Get child job IDs
        List<Long> childJobIds = backtestJobRepository.findByParentSweepJobId(sweepJobId)
                .stream()
                .map(BacktestJob::getId)
                .toList();
        responseBuilder.childJobIds(childJobIds);

        // Add best result if available
        if (sweep.getBestJobId() != null) {
            Optional<BacktestJob> bestJobOptional = backtestJobRepository.findById(sweep.getBestJobId());
            if (bestJobOptional.isPresent()) {
                BacktestJob bestJob = bestJobOptional.get();
                Optional<BacktestResult> resultOptional = backtestResultRepository.findByJobId(bestJob.getId());

                if (resultOptional.isPresent()) {
                    BacktestResult result = resultOptional.get();
                    ParameterSweepResponse.BestJobResult bestResult = ParameterSweepResponse.BestJobResult.builder()
                            .jobId(bestJob.getId())
                            .strategyName(bestJob.getStrategyName())
                            .parameters(bestJob.getParametersJson())
                            .totalReturn(result.getTotalReturn())
                            .cagr(result.getCagr())
                            .volatility(result.getVolatility())
                            .sharpeRatio(result.getSharpeRatio())
                            .sortinoRatio(result.getSortinoRatio())
                            .maxDrawdown(result.getMaxDrawdown())
                            .winRate(result.getWinRate())
                            .optimizationMetricValue(sweep.getBestMetricValue())
                            .build();

                    responseBuilder.bestResult(bestResult);
                }
            }
        }

        return responseBuilder.build();
    }

    /**
     * Generate idempotency key for child job.
     */
    private String generateIdempotencyKey(Long sweepJobId, String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = sweepJobId + "|" + String.join("|", parts);
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return "sweep_" + hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate idempotency key", e);
        }
    }
}
