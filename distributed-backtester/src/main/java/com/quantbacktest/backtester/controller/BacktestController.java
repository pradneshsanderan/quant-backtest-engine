package com.quantbacktest.backtester.controller;

import com.quantbacktest.backtester.controller.dto.BacktestSubmissionRequest;
import com.quantbacktest.backtester.controller.dto.BacktestSubmissionResponse;
import com.quantbacktest.backtester.service.BacktestService;
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
}
