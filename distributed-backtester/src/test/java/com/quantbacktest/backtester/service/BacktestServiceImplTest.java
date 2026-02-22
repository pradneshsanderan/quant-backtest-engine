package com.quantbacktest.backtester.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionRequest;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionResponse;
import com.quantbacktest.backtester.domain.BacktestJob;
import com.quantbacktest.backtester.domain.BacktestResult;
import com.quantbacktest.backtester.domain.JobStatus;
import com.quantbacktest.backtester.infrastructure.QueueService;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.repository.BacktestResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BacktestServiceImpl focusing on idempotency and job lifecycle.
 */
@ExtendWith(MockitoExtension.class)
class BacktestServiceImplTest {

    @Mock
    private BacktestJobRepository backtestJobRepository;

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @Mock
    private QueueService queueService;

    @Mock
    private BacktestMetricsService metricsService;

    private BacktestServiceImpl backtestService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JavaTimeModule for LocalDate support
        backtestService = new BacktestServiceImpl(
                backtestJobRepository,
                backtestResultRepository,
                queueService,
                objectMapper,
                metricsService);
    }

    @Test
    void testSubmitBacktest_NewJob_Success() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        when(backtestJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(backtestJobRepository.save(any())).thenAnswer(invocation -> {
            BacktestJob job = invocation.getArgument(0);
            job.setId(1L);
            return job;
        });

        // Act
        BacktestSubmissionResponse response = backtestService.submitBacktest(request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getJobId());
        assertEquals(JobStatus.QUEUED, response.getStatus());
        assertEquals("Job queued successfully", response.getMessage());
        assertFalse(response.getIsExisting());

        verify(backtestJobRepository).findByIdempotencyKey(any());
        verify(backtestJobRepository, times(2)).save(any());
        verify(queueService).push(1L);
        verify(metricsService).recordJobSubmitted();
    }

    @Test
    void testSubmitBacktest_ExistingCompleted_ReturnsCachedResults() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        BacktestJob existingJob = createCompletedJob();
        BacktestResult result = createBacktestResult();

        when(backtestJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existingJob));
        when(backtestResultRepository.findByJobId(1L)).thenReturn(Optional.of(result));

        // Act
        BacktestSubmissionResponse response = backtestService.submitBacktest(request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getJobId());
        assertEquals(JobStatus.COMPLETED, response.getStatus());
        assertTrue(response.getIsExisting());
        assertEquals(new BigDecimal("15.5"), response.getTotalReturn());
        assertEquals(new BigDecimal("12.3"), response.getCagr());
        assertEquals(new BigDecimal("18.7"), response.getVolatility());

        verify(backtestJobRepository).findByIdempotencyKey(any());
        verify(backtestJobRepository, never()).save(any());
        verify(queueService, never()).push(any());
        verify(metricsService, never()).recordJobSubmitted();
    }

    @Test
    void testSubmitBacktest_ExistingQueued_ReturnsExistingJob() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        BacktestJob existingJob = BacktestJob.builder()
                .id(1L)
                .status(JobStatus.QUEUED)
                .build();

        when(backtestJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existingJob));

        // Act
        BacktestSubmissionResponse response = backtestService.submitBacktest(request);

        // Assert
        assertEquals(1L, response.getJobId());
        assertEquals(JobStatus.QUEUED, response.getStatus());
        assertTrue(response.getIsExisting());
        assertEquals("Job is queued and waiting for processing", response.getMessage());

        verify(queueService, never()).push(any());
    }

    @Test
    void testSubmitBacktest_ExistingRunning_ReturnsExistingJob() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        BacktestJob existingJob = BacktestJob.builder()
                .id(1L)
                .status(JobStatus.RUNNING)
                .build();

        when(backtestJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existingJob));

        // Act
        BacktestSubmissionResponse response = backtestService.submitBacktest(request);

        // Assert
        assertEquals(1L, response.getJobId());
        assertEquals(JobStatus.RUNNING, response.getStatus());
        assertTrue(response.getIsExisting());
        assertEquals("Job is currently being processed", response.getMessage());
    }

    @Test
    void testSubmitBacktest_ExistingFailed_ReturnsFailureInfo() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        BacktestJob existingJob = BacktestJob.builder()
                .id(1L)
                .status(JobStatus.FAILED)
                .retryCount(3)
                .build();

        when(backtestJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existingJob));

        // Act
        BacktestSubmissionResponse response = backtestService.submitBacktest(request);

        // Assert
        assertEquals(1L, response.getJobId());
        assertEquals(JobStatus.FAILED, response.getStatus());
        assertTrue(response.getIsExisting());
        assertTrue(response.getMessage().contains("3 attempts"));
    }

    @Test
    void testSubmitBacktest_SameRequest_GeneratesSameIdempotencyKey() {
        // Arrange
        BacktestSubmissionRequest request1 = createValidRequest();
        BacktestSubmissionRequest request2 = createValidRequest();

        when(backtestJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(backtestJobRepository.save(any())).thenAnswer(invocation -> {
            BacktestJob job = invocation.getArgument(0);
            job.setId(1L);
            return job;
        });

        ArgumentCaptor<String> idempotencyKeyCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        backtestService.submitBacktest(request1);
        verify(backtestJobRepository).findByIdempotencyKey(idempotencyKeyCaptor.capture());
        String key1 = idempotencyKeyCaptor.getValue();

        backtestService.submitBacktest(request2);
        verify(backtestJobRepository, times(2)).findByIdempotencyKey(idempotencyKeyCaptor.capture());
        String key2 = idempotencyKeyCaptor.getValue();

        // Assert
        assertEquals(key1, key2, "Same requests should generate same idempotency key");
    }

    @Test
    void testSubmitBacktest_DifferentRequest_GeneratesDifferentIdempotencyKey() {
        // Arrange
        BacktestSubmissionRequest request1 = createValidRequest();
        BacktestSubmissionRequest request2 = createValidRequest();
        request2.setSymbol("TSLA"); // Different symbol

        when(backtestJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(backtestJobRepository.save(any())).thenAnswer(invocation -> {
            BacktestJob job = invocation.getArgument(0);
            job.setId(1L);
            return job;
        });

        ArgumentCaptor<String> idempotencyKeyCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        backtestService.submitBacktest(request1);
        verify(backtestJobRepository).findByIdempotencyKey(idempotencyKeyCaptor.capture());
        String key1 = idempotencyKeyCaptor.getValue();

        backtestService.submitBacktest(request2);
        verify(backtestJobRepository, times(2)).findByIdempotencyKey(idempotencyKeyCaptor.capture());
        String key2 = idempotencyKeyCaptor.getValue();

        // Assert
        assertNotEquals(key1, key2, "Different requests should generate different idempotency keys");
    }

    @Test
    void testSubmitBacktest_CreatesJobWithCorrectParameters() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        when(backtestJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        ArgumentCaptor<BacktestJob> jobCaptor = ArgumentCaptor.forClass(BacktestJob.class);
        when(backtestJobRepository.save(jobCaptor.capture())).thenAnswer(invocation -> {
            BacktestJob job = invocation.getArgument(0);
            job.setId(1L);
            return job;
        });

        // Act
        backtestService.submitBacktest(request);

        // Assert
        BacktestJob capturedJob = jobCaptor.getAllValues().get(0);
        assertEquals("MovingAverageCrossover", capturedJob.getStrategyName());
        assertEquals("AAPL", capturedJob.getSymbol());
        assertEquals(LocalDate.of(2024, 1, 1), capturedJob.getStartDate());
        assertEquals(LocalDate.of(2024, 12, 31), capturedJob.getEndDate());
        assertEquals(JobStatus.SUBMITTED, capturedJob.getStatus());
        assertEquals(0, capturedJob.getRetryCount());
        assertNotNull(capturedJob.getParametersJson());
        assertNotNull(capturedJob.getIdempotencyKey());
    }

    private BacktestSubmissionRequest createValidRequest() {
        Map<String, Object> params = new HashMap<>();
        params.put("shortPeriod", 5);
        params.put("longPeriod", 20);

        return BacktestSubmissionRequest.builder()
                .strategyName("MovingAverageCrossover")
                .symbol("AAPL")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .parameters(params)
                .initialCapital(new BigDecimal("10000.00"))
                .build();
    }

    private BacktestJob createCompletedJob() {
        return BacktestJob.builder()
                .id(1L)
                .strategyName("MovingAverageCrossover")
                .symbol("AAPL")
                .status(JobStatus.COMPLETED)
                .retryCount(0)
                .build();
    }

    private BacktestResult createBacktestResult() {
        BacktestJob job = createCompletedJob();
        return BacktestResult.builder()
                .id(1L)
                .job(job)
                .totalReturn(new BigDecimal("15.5"))
                .cagr(new BigDecimal("12.3"))
                .volatility(new BigDecimal("18.7"))
                .sharpeRatio(new BigDecimal("1.45"))
                .sortinoRatio(new BigDecimal("2.12"))
                .maxDrawdown(new BigDecimal("-8.5"))
                .winRate(new BigDecimal("0.65"))
                .build();
    }
}
