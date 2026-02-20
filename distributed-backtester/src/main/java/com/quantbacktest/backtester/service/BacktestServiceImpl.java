package com.quantbacktest.backtester.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionRequest;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionResponse;
import com.quantbacktest.backtester.domain.BacktestJob;
import com.quantbacktest.backtester.domain.BacktestResult;
import com.quantbacktest.backtester.domain.JobStatus;
import com.quantbacktest.backtester.infrastructure.QueueService;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.repository.BacktestResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Implementation of BacktestService for managing backtest job submissions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestServiceImpl implements BacktestService {

    private final BacktestJobRepository backtestJobRepository;
    private final BacktestResultRepository backtestResultRepository;
    private final QueueService queueService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public BacktestSubmissionResponse submitBacktest(BacktestSubmissionRequest request) {
        log.info("Received backtest submission for strategy: {}, symbol: {}",
                request.getStrategyName(), request.getSymbol());

        // Generate idempotency key from request payload
        String idempotencyKey = generateIdempotencyKey(request);
        log.debug("Generated idempotency key: {}", idempotencyKey);

        // Check if job already exists
        Optional<BacktestJob> existingJob = backtestJobRepository.findByIdempotencyKey(idempotencyKey);

        if (existingJob.isPresent()) {
            BacktestJob job = existingJob.get();
            log.info("Idempotent request detected for job ID: {} with status: {}",
                    job.getId(), job.getStatus());

            return handleExistingJob(job);
        }

        // Create new backtest job
        BacktestJob newJob = createBacktestJob(request, idempotencyKey);
        BacktestJob savedJob = backtestJobRepository.save(newJob);

        log.info("Created new backtest job with ID: {}", savedJob.getId());

        // Push job to Redis queue and update status
        queueService.push(savedJob.getId());
        savedJob.setStatus(JobStatus.QUEUED);
        savedJob.setUpdatedAt(LocalDateTime.now());
        backtestJobRepository.save(savedJob);

        log.info("Job {} pushed to queue with status QUEUED", savedJob.getId());

        return BacktestSubmissionResponse.builder()
                .jobId(savedJob.getId())
                .status(savedJob.getStatus())
                .message("Job queued successfully")
                .isExisting(false)
                .build();
    }

    /**
     * Handle existing job based on its current status.
     * Provides true idempotent behavior with appropriate responses.
     */
    private BacktestSubmissionResponse handleExistingJob(BacktestJob job) {
        return switch (job.getStatus()) {
            case COMPLETED -> {
                log.info("Job {} is COMPLETED. Fetching and returning results.", job.getId());
                Optional<BacktestResult> result = backtestResultRepository.findByJobId(job.getId());

                if (result.isPresent()) {
                    BacktestResult r = result.get();
                    yield BacktestSubmissionResponse.builder()
                            .jobId(job.getId())
                            .status(job.getStatus())
                            .message("Job already completed. Returning cached results.")
                            .isExisting(true)
                            .totalReturn(r.getTotalReturn())
                            .sharpeRatio(r.getSharpeRatio())
                            .maxDrawdown(r.getMaxDrawdown())
                            .winRate(r.getWinRate())
                            .build();
                } else {
                    log.warn("Job {} marked COMPLETED but no result found", job.getId());
                    yield BacktestSubmissionResponse.builder()
                            .jobId(job.getId())
                            .status(job.getStatus())
                            .message("Job completed but results not found")
                            .isExisting(true)
                            .build();
                }
            }

            case RUNNING -> {
                log.info("Job {} is currently RUNNING. Returning job info.", job.getId());
                yield BacktestSubmissionResponse.builder()
                        .jobId(job.getId())
                        .status(job.getStatus())
                        .message("Job is currently being processed")
                        .isExisting(true)
                        .build();
            }

            case QUEUED -> {
                log.info("Job {} is QUEUED. Returning job info.", job.getId());
                yield BacktestSubmissionResponse.builder()
                        .jobId(job.getId())
                        .status(job.getStatus())
                        .message("Job is queued and waiting for processing")
                        .isExisting(true)
                        .build();
            }

            case FAILED -> {
                log.info("Job {} has FAILED. Returning failure info.", job.getId());
                yield BacktestSubmissionResponse.builder()
                        .jobId(job.getId())
                        .status(job.getStatus())
                        .message("Job previously failed after " + job.getRetryCount() + " attempts")
                        .isExisting(true)
                        .build();
            }

            case SUBMITTED -> {
                log.info("Job {} is in SUBMITTED state. Returning job info.", job.getId());
                yield BacktestSubmissionResponse.builder()
                        .jobId(job.getId())
                        .status(job.getStatus())
                        .message("Job submitted and awaiting queue placement")
                        .isExisting(true)
                        .build();
            }
        };
    }

    /**
     * Generate SHA-256 hash of the request payload for idempotency.
     */
    private String generateIdempotencyKey(BacktestSubmissionRequest request) {
        try {
            // Serialize request to JSON
            String jsonPayload = objectMapper.writeValueAsString(request);

            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jsonPayload.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request to JSON", e);
            throw new RuntimeException("Failed to generate idempotency key", e);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to generate idempotency key", e);
        }
    }

    /**
     * Create a new BacktestJob entity from the request.
     */
    private BacktestJob createBacktestJob(BacktestSubmissionRequest request, String idempotencyKey) {
        try {
            // Convert parameters map to JSON string
            String parametersJson = objectMapper.writeValueAsString(request.getParameters());

            return BacktestJob.builder()
                    .strategyName(request.getStrategyName())
                    .symbol(request.getSymbol())
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .parametersJson(parametersJson)
                    .status(JobStatus.SUBMITTED)
                    .idempotencyKey(idempotencyKey)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize parameters to JSON", e);
            throw new RuntimeException("Failed to create backtest job", e);
        }
    }
}
