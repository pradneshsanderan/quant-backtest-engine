package com.quantbacktest.backtester.controller;

import com.quantbacktest.backtester.controller.dto.BacktestSubmissionRequest;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionResponse;
import com.quantbacktest.backtester.controller.dto.ParameterSweepRequest;
import com.quantbacktest.backtester.controller.dto.ParameterSweepResponse;
import com.quantbacktest.backtester.service.BacktestService;
import com.quantbacktest.backtester.service.ParameterSweepService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for backtest job operations.
 */
@RestController
@RequestMapping("/backtests")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestService backtestService;
    private final ParameterSweepService parameterSweepService;

    /**
     * Submit a new backtest job.
     *
     * @param request the backtest submission request
     * @return the submission response with job ID and status
     */
    @PostMapping
    public ResponseEntity<BacktestSubmissionResponse> submitBacktest(
            @Valid @RequestBody BacktestSubmissionRequest request) {

        log.info("POST /backtests - Strategy: {}, Symbol: {}",
                request.getStrategyName(), request.getSymbol());

        BacktestSubmissionResponse response = backtestService.submitBacktest(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Submit a parameter sweep job with multiple parameter combinations.
     *
     * @param request the parameter sweep request
     * @return the sweep submission response
     */
    @PostMapping("/sweeps")
    public ResponseEntity<ParameterSweepResponse> submitParameterSweep(
            @Valid @RequestBody ParameterSweepRequest request) {

        log.info("POST /backtests/sweeps - Name: {}, Total strategies: {}",
                request.getName(), request.getStrategies().size());

        ParameterSweepResponse response = parameterSweepService.submitSweep(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get the status and results of a parameter sweep job.
     *
     * @param sweepId the sweep job ID
     * @return the sweep status and results
     */
    @GetMapping("/sweeps/{sweepId}")
    public ResponseEntity<ParameterSweepResponse> getSweepStatus(@PathVariable Long sweepId) {

        log.info("GET /backtests/sweeps/{} - Fetching sweep status", sweepId);

        ParameterSweepResponse response = parameterSweepService.getSweepStatus(sweepId);

        return ResponseEntity.ok(response);
    }
}
