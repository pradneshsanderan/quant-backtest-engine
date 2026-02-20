package com.quantbacktest.backtester.domain;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculator for backtest performance metrics.
 */
@Slf4j
public class PerformanceMetrics {

    /**
     * Calculate total return percentage.
     */
    public static BigDecimal calculateTotalReturn(BigDecimal initialCapital, BigDecimal finalValue) {
        if (initialCapital.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return finalValue.subtract(initialCapital)
                .divide(initialCapital, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Calculate Sharpe ratio (simplified - assuming risk-free rate of 0).
     */
    public static BigDecimal calculateSharpeRatio(List<BigDecimal> portfolioValues) {
        if (portfolioValues.size() < 2) {
            return BigDecimal.ZERO;
        }

        // Calculate daily returns
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < portfolioValues.size(); i++) {
            BigDecimal prevValue = portfolioValues.get(i - 1);
            BigDecimal currentValue = portfolioValues.get(i);

            if (prevValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyReturn = currentValue.subtract(prevValue)
                        .divide(prevValue, 6, RoundingMode.HALF_UP);
                returns.add(dailyReturn);
            }
        }

        if (returns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Calculate mean return
        BigDecimal sumReturns = returns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal meanReturn = sumReturns.divide(
                BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);

        // Calculate standard deviation
        BigDecimal sumSquaredDiff = returns.stream()
                .map(r -> r.subtract(meanReturn).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double variance = sumSquaredDiff.divide(
                BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP)
                .doubleValue();
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) {
            return BigDecimal.ZERO;
        }

        // Sharpe ratio = mean return / std dev (annualized assuming 252 trading days)
        double sharpe = (meanReturn.doubleValue() / stdDev) * Math.sqrt(252);
        return BigDecimal.valueOf(sharpe).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Calculate maximum drawdown percentage.
     */
    public static BigDecimal calculateMaxDrawdown(List<BigDecimal> portfolioValues) {
        if (portfolioValues.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal peak = portfolioValues.get(0);

        for (BigDecimal value : portfolioValues) {
            if (value.compareTo(peak) > 0) {
                peak = value;
            }

            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = peak.subtract(value)
                        .divide(peak, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
        }

        return maxDrawdown.negate(); // Return as negative percentage
    }

    /**
     * Calculate win rate (percentage of profitable trades).
     */
    public static BigDecimal calculateWinRate(List<Trade> trades) {
        if (trades.size() < 2) {
            return BigDecimal.ZERO;
        }

        int winningTrades = 0;
        int totalRoundTrips = 0;

        // Pair buy and sell trades
        for (int i = 0; i < trades.size() - 1; i++) {
            Trade trade = trades.get(i);
            Trade nextTrade = trades.get(i + 1);

            if (trade.getType() == Trade.TradeType.BUY &&
                    nextTrade.getType() == Trade.TradeType.SELL) {
                totalRoundTrips++;

                BigDecimal profit = nextTrade.getPrice().subtract(trade.getPrice())
                        .multiply(BigDecimal.valueOf(trade.getQuantity()));

                if (profit.compareTo(BigDecimal.ZERO) > 0) {
                    winningTrades++;
                }
            }
        }

        if (totalRoundTrips == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(winningTrades)
                .divide(BigDecimal.valueOf(totalRoundTrips), 4, RoundingMode.HALF_UP);
    }

    /**
     * Calculate CAGR (Compound Annual Growth Rate).
     * CAGR = (Ending Value / Beginning Value)^(1 / Years) - 1
     */
    public static BigDecimal calculateCAGR(BigDecimal initialCapital, BigDecimal finalValue, int tradingDays) {
        if (initialCapital.compareTo(BigDecimal.ZERO) <= 0 || tradingDays <= 0) {
            return BigDecimal.ZERO;
        }

        if (finalValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.valueOf(-100.0); // Total loss
        }

        // Convert trading days to years (assuming 252 trading days per year)
        double years = tradingDays / 252.0;

        if (years < 0.01) { // Less than ~2-3 trading days
            return BigDecimal.ZERO;
        }

        double ratio = finalValue.doubleValue() / initialCapital.doubleValue();
        double cagr = (Math.pow(ratio, 1.0 / years) - 1.0) * 100.0;

        return BigDecimal.valueOf(cagr).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Calculate annualized volatility (standard deviation of returns).
     */
    public static BigDecimal calculateVolatility(List<BigDecimal> portfolioValues) {
        if (portfolioValues.size() < 2) {
            return BigDecimal.ZERO;
        }

        // Calculate daily returns
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < portfolioValues.size(); i++) {
            BigDecimal prevValue = portfolioValues.get(i - 1);
            BigDecimal currentValue = portfolioValues.get(i);

            if (prevValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyReturn = currentValue.subtract(prevValue)
                        .divide(prevValue, 6, RoundingMode.HALF_UP);
                returns.add(dailyReturn);
            }
        }

        if (returns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Calculate mean return
        BigDecimal sumReturns = returns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal meanReturn = sumReturns.divide(
                BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);

        // Calculate standard deviation
        BigDecimal sumSquaredDiff = returns.stream()
                .map(r -> r.subtract(meanReturn).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double variance = sumSquaredDiff.divide(
                BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP)
                .doubleValue();
        double stdDev = Math.sqrt(variance);

        // Annualize volatility (multiply by sqrt(252))
        double annualizedVolatility = stdDev * Math.sqrt(252) * 100.0;

        return BigDecimal.valueOf(annualizedVolatility).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Calculate Sortino Ratio (like Sharpe but only penalizes downside volatility).
     * Assumes risk-free rate of 0.
     */
    public static BigDecimal calculateSortinoRatio(List<BigDecimal> portfolioValues) {
        if (portfolioValues.size() < 2) {
            return BigDecimal.ZERO;
        }

        // Calculate daily returns
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < portfolioValues.size(); i++) {
            BigDecimal prevValue = portfolioValues.get(i - 1);
            BigDecimal currentValue = portfolioValues.get(i);

            if (prevValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyReturn = currentValue.subtract(prevValue)
                        .divide(prevValue, 6, RoundingMode.HALF_UP);
                returns.add(dailyReturn);
            }
        }

        if (returns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Calculate mean return
        BigDecimal sumReturns = returns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal meanReturn = sumReturns.divide(
                BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);

        // Calculate downside deviation (only negative returns)
        List<BigDecimal> downsideReturns = returns.stream()
                .filter(r -> r.compareTo(BigDecimal.ZERO) < 0)
                .toList();

        if (downsideReturns.isEmpty()) {
            // No downside, return a high Sortino ratio
            return BigDecimal.valueOf(999.9999).setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal sumSquaredDownside = downsideReturns.stream()
                .map(r -> r.pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double downsideVariance = sumSquaredDownside.divide(
                BigDecimal.valueOf(downsideReturns.size()), 6, RoundingMode.HALF_UP)
                .doubleValue();
        double downsideDeviation = Math.sqrt(downsideVariance);

        if (downsideDeviation == 0) {
            return BigDecimal.ZERO;
        }

        // Sortino ratio = mean return / downside deviation (annualized)
        double sortino = (meanReturn.doubleValue() / downsideDeviation) * Math.sqrt(252);
        return BigDecimal.valueOf(sortino).setScale(4, RoundingMode.HALF_UP);
    }
}
