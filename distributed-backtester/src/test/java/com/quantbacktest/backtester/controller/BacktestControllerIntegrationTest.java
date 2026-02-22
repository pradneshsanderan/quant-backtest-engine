package com.quantbacktest.backtester.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionRequest;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionResponse;
import com.quantbacktest.backtester.domain.JobStatus;
import com.quantbacktest.backtester.service.BacktestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for BacktestController REST endpoints.
 */
@WebMvcTest(BacktestController.class)
class BacktestControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BacktestService backtestService;

    @MockBean
    private com.quantbacktest.backtester.service.ParameterSweepService parameterSweepService;

    @Test
    void testSubmitBacktest_Success() throws Exception {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        BacktestSubmissionResponse mockResponse = BacktestSubmissionResponse.builder()
                .jobId(1L)
                .status(JobStatus.QUEUED)
                .message("Job queued successfully")
                .isExisting(false)
                .build();

        when(backtestService.submitBacktest(any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("Job queued successfully"))
                .andExpect(jsonPath("$.isExisting").value(false));
    }

    @Test
    void testSubmitBacktest_IdempotentRequest() throws Exception {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        BacktestSubmissionResponse mockResponse = BacktestSubmissionResponse.builder()
                .jobId(1L)
                .status(JobStatus.COMPLETED)
                .message("Job already completed. Returning cached results.")
                .isExisting(true)
                .totalReturn(new BigDecimal("15.5"))
                .sharpeRatio(new BigDecimal("1.25"))
                .maxDrawdown(new BigDecimal("-8.3"))
                .build();

        when(backtestService.submitBacktest(any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.isExisting").value(true))
                .andExpect(jsonPath("$.totalReturn").value(15.5))
                .andExpect(jsonPath("$.sharpeRatio").value(1.25));
    }

    @Test
    void testSubmitBacktest_MissingStrategyName() throws Exception {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setStrategyName(null);

        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSubmitBacktest_MissingSymbol() throws Exception {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setSymbol("");

        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSubmitBacktest_MissingStartDate() throws Exception {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setStartDate(null);

        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSubmitBacktest_MissingEndDate() throws Exception {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setEndDate(null);

        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSubmitBacktest_NullParameters() throws Exception {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setParameters(null);

        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSubmitBacktest_NegativeInitialCapital() throws Exception {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setInitialCapital(new BigDecimal("-1000"));

        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSubmitBacktest_ZeroInitialCapital() throws Exception {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setInitialCapital(BigDecimal.ZERO);

        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSubmitBacktest_InvalidJson() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSubmitBacktest_BuyAndHoldStrategy() throws Exception {
        // Arrange
        BacktestSubmissionRequest request = BacktestSubmissionRequest.builder()
                .strategyName("BuyAndHold")
                .symbol("AAPL")
                .startDate(LocalDate.of(2023, 1, 1))
                .endDate(LocalDate.of(2023, 12, 31))
                .parameters(new HashMap<>()) // No parameters for buy and hold
                .initialCapital(new BigDecimal("10000.00"))
                .build();

        BacktestSubmissionResponse mockResponse = BacktestSubmissionResponse.builder()
                .jobId(2L)
                .status(JobStatus.QUEUED)
                .message("Job queued successfully")
                .isExisting(false)
                .build();

        when(backtestService.submitBacktest(any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(2))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void testSubmitBacktest_MovingAverageCrossoverStrategy() throws Exception {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("shortPeriod", 10);
        params.put("longPeriod", 20);

        BacktestSubmissionRequest request = BacktestSubmissionRequest.builder()
                .strategyName("MovingAverageCrossover")
                .symbol("TSLA")
                .startDate(LocalDate.of(2023, 1, 1))
                .endDate(LocalDate.of(2023, 12, 31))
                .parameters(params)
                .initialCapital(new BigDecimal("50000.00"))
                .build();

        BacktestSubmissionResponse mockResponse = BacktestSubmissionResponse.builder()
                .jobId(3L)
                .status(JobStatus.QUEUED)
                .message("Job queued successfully")
                .isExisting(false)
                .build();

        when(backtestService.submitBacktest(any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/backtests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(3))
                .andExpect(jsonPath("$.status").value("QUEUED"));
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
}
