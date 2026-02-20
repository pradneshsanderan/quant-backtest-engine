package com.quantbacktest.backtester.domain;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Core backtesting engine that executes strategies against historical data.
 */
@Slf4j
public class BacktestEngine {

    /**
     * Run a backtest with the given parameters.
     */
    public BacktestResult runBacktest(BacktestConfig config) {
        log.info("Starting backtest - Strategy: {}, Data points: {}",
                config.getStrategy().getName(), config.getMarketData().size());

        // Initialize portfolio
        Portfolio portfolio = Portfolio.builder()
                .cash(config.getInitialCapital())
                .shares(0)
                .trades(new ArrayList<>())
                .initialCapital(config.getInitialCapital())
                .build();

        List<BigDecimal> portfolioValues = new ArrayList<>();

        // Process each market data point in chronological order
        for (MarketData data : config.getMarketData()) {
            config.getStrategy().onTick(data, portfolio);

            // Track portfolio value over time
            BigDecimal portfolioValue = portfolio.getPortfolioValue(data.getClose());
            portfolioValues.add(portfolioValue);
        }

        // Finalize strategy
        config.getStrategy().onFinish(portfolio);

        // Get final portfolio value
        MarketData lastData = config.getMarketData().get(config.getMarketData().size() - 1);
        BigDecimal finalValue = portfolio.getPortfolioValue(lastData.getClose());

        // Calculate all performance metrics
        int tradingDays = config.getMarketData().size();

        BigDecimal totalReturn = PerformanceMetrics.calculateTotalReturn(
                config.getInitialCapital(), finalValue);

        BigDecimal cagr = PerformanceMetrics.calculateCAGR(
                config.getInitialCapital(), finalValue, tradingDays);

        BigDecimal volatility = PerformanceMetrics.calculateVolatility(portfolioValues);

        BigDecimal sharpeRatio = PerformanceMetrics.calculateSharpeRatio(portfolioValues);

        BigDecimal sortinoRatio = PerformanceMetrics.calculateSortinoRatio(portfolioValues);

        BigDecimal maxDrawdown = PerformanceMetrics.calculateMaxDrawdown(portfolioValues);

        BigDecimal winRate = PerformanceMetrics.calculateWinRate(portfolio.getTrades());

        log.info("Backtest completed - Total Return: {}%, CAGR: {}%, Volatility: {}%, " +
                "Sharpe: {}, Sortino: {}, Max DD: {}%, Win Rate: {}%",
                totalReturn, cagr, volatility, sharpeRatio, sortinoRatio, maxDrawdown,
                winRate.multiply(BigDecimal.valueOf(100)));

        return BacktestResult.builder()
                .totalReturn(totalReturn)
                .cagr(cagr)
                .volatility(volatility)
                .sharpeRatio(sharpeRatio)
                .sortinoRatio(sortinoRatio)
                .maxDrawdown(maxDrawdown)
                .winRate(winRate)
                .finalValue(finalValue)
                .totalTrades(portfolio.getTrades().size())
                .trades(portfolio.getTrades())
                .equityCurve(portfolioValues)
                .build();
    }

    /**
     * Configuration for a backtest run.
     */
    @Data
    @Builder
    public static class BacktestConfig {
        private Strategy strategy;
        private List<MarketData> marketData;
        private BigDecimal initialCapital;
    }

    /**
     * Result of a backtest run.
     */
    @Data
    @Builder
    public static class BacktestResult {
        private BigDecimal totalReturn;
        private BigDecimal cagr;
        private BigDecimal volatility;
        private BigDecimal sharpeRatio;
        private BigDecimal sortinoRatio;
        private BigDecimal maxDrawdown;
        private BigDecimal winRate;
        private BigDecimal finalValue;
        private int totalTrades;
        private List<Trade> trades;
        private List<BigDecimal> equityCurve;
    }
}
