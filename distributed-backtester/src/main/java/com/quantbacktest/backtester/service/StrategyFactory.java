package com.quantbacktest.backtester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbacktest.backtester.domain.BuyAndHoldStrategy;
import com.quantbacktest.backtester.domain.MovingAverageCrossoverStrategy;
import com.quantbacktest.backtester.domain.Strategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Factory for creating strategy instances based on name and parameters.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyFactory {

    private final ObjectMapper objectMapper;

    /**
     * Create a strategy instance from name and JSON parameters.
     */
    public Strategy createStrategy(String strategyName, String parametersJson) {
        log.info("Creating strategy: {} with parameters: {}", strategyName, parametersJson);

        try {
            JsonNode params = objectMapper.readTree(parametersJson);

            return switch (strategyName.toLowerCase()) {
                case "buyandhold", "buy_and_hold" -> new BuyAndHoldStrategy();

                case "movingaveragecrossover", "ma_crossover" -> {
                    int shortPeriod = params.has("shortPeriod") ? params.get("shortPeriod").asInt(10) : 10;
                    int longPeriod = params.has("longPeriod") ? params.get("longPeriod").asInt(50) : 50;
                    yield new MovingAverageCrossoverStrategy(shortPeriod, longPeriod);
                }

                default -> {
                    log.warn("Unknown strategy: {}, defaulting to BuyAndHold", strategyName);
                    yield new BuyAndHoldStrategy();
                }
            };

        } catch (Exception e) {
            log.error("Failed to parse strategy parameters, using defaults: {}", e.getMessage());
            return new BuyAndHoldStrategy();
        }
    }
}
