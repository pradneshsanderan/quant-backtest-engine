package com.quantbacktest.backtester.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PerformanceMetrics calculations.
 */
class PerformanceMetricsTest {

    @Test
    void testCalculateTotalReturn_WithProfit() {
        BigDecimal initialCapital = new BigDecimal("10000");
        BigDecimal finalValue = new BigDecimal("12000");

        BigDecimal totalReturn = PerformanceMetrics.calculateTotalReturn(initialCapital, finalValue);

        assertEquals(new BigDecimal("20.0000"), totalReturn);
    }

    @Test
    void testCalculateTotalReturn_WithLoss() {
        BigDecimal initialCapital = new BigDecimal("10000");
        BigDecimal finalValue = new BigDecimal("8000");

        BigDecimal totalReturn = PerformanceMetrics.calculateTotalReturn(initialCapital, finalValue);

        assertEquals(new BigDecimal("-20.0000"), totalReturn);
    }

    @Test
    void testCalculateTotalReturn_NoChange() {
        BigDecimal initialCapital = new BigDecimal("10000");
        BigDecimal finalValue = new BigDecimal("10000");

        BigDecimal totalReturn = PerformanceMetrics.calculateTotalReturn(initialCapital, finalValue);

        assertEquals(new BigDecimal("0.0000"), totalReturn);
    }

    @Test
    void testCalculateTotalReturn_ZeroInitialCapital() {
        BigDecimal initialCapital = BigDecimal.ZERO;
        BigDecimal finalValue = new BigDecimal("1000");

        BigDecimal totalReturn = PerformanceMetrics.calculateTotalReturn(initialCapital, finalValue);

        assertEquals(BigDecimal.ZERO, totalReturn);
    }

    @Test
    void testCalculateSharpeRatio_WithPositiveReturns() {
        List<BigDecimal> portfolioValues = Arrays.asList(
                new BigDecimal("10000"),
                new BigDecimal("10100"),
                new BigDecimal("10200"),
                new BigDecimal("10300"),
                new BigDecimal("10400"),
                new BigDecimal("10500"));

        BigDecimal sharpeRatio = PerformanceMetrics.calculateSharpeRatio(portfolioValues);

        assertNotNull(sharpeRatio);
        assertTrue(sharpeRatio.compareTo(BigDecimal.ZERO) > 0, "Sharpe ratio should be positive for consistent gains");
    }

    @Test
    void testCalculateSharpeRatio_WithVolatileReturns() {
        List<BigDecimal> portfolioValues = Arrays.asList(
                new BigDecimal("10000"),
                new BigDecimal("10500"),
                new BigDecimal("9800"),
                new BigDecimal("10300"),
                new BigDecimal("9900"),
                new BigDecimal("10600"));

        BigDecimal sharpeRatio = PerformanceMetrics.calculateSharpeRatio(portfolioValues);

        assertNotNull(sharpeRatio);
        // Should be lower than consistent gains due to higher volatility
    }

    @Test
    void testCalculateSharpeRatio_InsufficientData() {
        List<BigDecimal> portfolioValues = List.of(new BigDecimal("10000"));

        BigDecimal sharpeRatio = PerformanceMetrics.calculateSharpeRatio(portfolioValues);

        assertEquals(BigDecimal.ZERO, sharpeRatio);
    }

    @Test
    void testCalculateSharpeRatio_EmptyList() {
        List<BigDecimal> portfolioValues = new ArrayList<>();

        BigDecimal sharpeRatio = PerformanceMetrics.calculateSharpeRatio(portfolioValues);

        assertEquals(BigDecimal.ZERO, sharpeRatio);
    }

    @Test
    void testCalculateMaxDrawdown_WithDrawdown() {
        List<BigDecimal> portfolioValues = Arrays.asList(
                new BigDecimal("10000"),
                new BigDecimal("12000"),
                new BigDecimal("11000"),
                new BigDecimal("9000"),
                new BigDecimal("10000"));

        BigDecimal maxDrawdown = PerformanceMetrics.calculateMaxDrawdown(portfolioValues);

        // Max drawdown from 12000 to 9000 = -25%
        assertTrue(maxDrawdown.compareTo(new BigDecimal("-20")) < 0, "Should have significant drawdown");
        assertTrue(maxDrawdown.compareTo(new BigDecimal("-30")) > 0, "Drawdown should be approximately -25%");
    }

    @Test
    void testCalculateMaxDrawdown_NoDrawdown() {
        List<BigDecimal> portfolioValues = Arrays.asList(
                new BigDecimal("10000"),
                new BigDecimal("10500"),
                new BigDecimal("11000"),
                new BigDecimal("11500"));

        BigDecimal maxDrawdown = PerformanceMetrics.calculateMaxDrawdown(portfolioValues);

        assertEquals(BigDecimal.ZERO, maxDrawdown);
    }

    @Test
    void testCalculateMaxDrawdown_EmptyList() {
        List<BigDecimal> portfolioValues = new ArrayList<>();

        BigDecimal maxDrawdown = PerformanceMetrics.calculateMaxDrawdown(portfolioValues);

        assertEquals(BigDecimal.ZERO, maxDrawdown);
    }

    @Test
    void testCalculateWinRate_AllWinningTrades() {
        List<Trade> trades = Arrays.asList(
                createTrade(Trade.TradeType.BUY, "100.00", 10),
                createTrade(Trade.TradeType.SELL, "110.00", 10),
                createTrade(Trade.TradeType.BUY, "105.00", 10),
                createTrade(Trade.TradeType.SELL, "115.00", 10));

        BigDecimal winRate = PerformanceMetrics.calculateWinRate(trades);

        assertEquals(new BigDecimal("1.0000"), winRate, "Win rate should be 100%");
    }

    @Test
    void testCalculateWinRate_MixedTrades() {
        List<Trade> trades = Arrays.asList(
                createTrade(Trade.TradeType.BUY, "100.00", 10),
                createTrade(Trade.TradeType.SELL, "110.00", 10), // Win
                createTrade(Trade.TradeType.BUY, "105.00", 10),
                createTrade(Trade.TradeType.SELL, "100.00", 10) // Loss
        );

        BigDecimal winRate = PerformanceMetrics.calculateWinRate(trades);

        assertEquals(new BigDecimal("0.5000"), winRate, "Win rate should be 50%");
    }

    @Test
    void testCalculateWinRate_InsufficientTrades() {
        List<Trade> trades = List.of(createTrade(Trade.TradeType.BUY, "100.00", 10));

        BigDecimal winRate = PerformanceMetrics.calculateWinRate(trades);

        assertEquals(BigDecimal.ZERO, winRate);
    }

    @Test
    void testCalculateWinRate_NoRoundTrips() {
        List<Trade> trades = Arrays.asList(
                createTrade(Trade.TradeType.BUY, "100.00", 10),
                createTrade(Trade.TradeType.BUY, "105.00", 10));

        BigDecimal winRate = PerformanceMetrics.calculateWinRate(trades);

        assertEquals(BigDecimal.ZERO, winRate);
    }

    @Test
    void testCalculateCAGR_PositiveGrowth() {
        BigDecimal initialCapital = new BigDecimal("10000");
        BigDecimal finalValue = new BigDecimal("15000");
        int tradingDays = 252; // 1 year

        BigDecimal cagr = PerformanceMetrics.calculateCAGR(initialCapital, finalValue, tradingDays);

        assertNotNull(cagr);
        assertTrue(cagr.compareTo(new BigDecimal("40")) > 0, "CAGR should be around 50%");
        assertTrue(cagr.compareTo(new BigDecimal("60")) < 0, "CAGR should be around 50%");
    }

    @Test
    void testCalculateCAGR_NegativeGrowth() {
        BigDecimal initialCapital = new BigDecimal("10000");
        BigDecimal finalValue = new BigDecimal("8000");
        int tradingDays = 252;

        BigDecimal cagr = PerformanceMetrics.calculateCAGR(initialCapital, finalValue, tradingDays);

        assertTrue(cagr.compareTo(BigDecimal.ZERO) < 0, "CAGR should be negative");
    }

    @Test
    void testCalculateCAGR_ZeroInitialCapital() {
        BigDecimal initialCapital = BigDecimal.ZERO;
        BigDecimal finalValue = new BigDecimal("10000");
        int tradingDays = 252;

        BigDecimal cagr = PerformanceMetrics.calculateCAGR(initialCapital, finalValue, tradingDays);

        assertEquals(BigDecimal.ZERO, cagr);
    }

    @Test
    void testCalculateCAGR_TotalLoss() {
        BigDecimal initialCapital = new BigDecimal("10000");
        BigDecimal finalValue = BigDecimal.ZERO;
        int tradingDays = 252;

        BigDecimal cagr = PerformanceMetrics.calculateCAGR(initialCapital, finalValue, tradingDays);

        assertEquals(new BigDecimal("-100.0000"), cagr);
    }

    @Test
    void testCalculateVolatility_ConsistentReturns() {
        List<BigDecimal> portfolioValues = Arrays.asList(
                new BigDecimal("10000"),
                new BigDecimal("10100"),
                new BigDecimal("10200"),
                new BigDecimal("10300"));

        BigDecimal volatility = PerformanceMetrics.calculateVolatility(portfolioValues);

        assertNotNull(volatility);
        assertTrue(volatility.compareTo(BigDecimal.ZERO) > 0, "Volatility should be positive");
        // Low volatility for consistent returns
    }

    @Test
    void testCalculateVolatility_HighVolatility() {
        List<BigDecimal> portfolioValues = Arrays.asList(
                new BigDecimal("10000"),
                new BigDecimal("12000"),
                new BigDecimal("8000"),
                new BigDecimal("11000"),
                new BigDecimal("9000"));

        BigDecimal volatility = PerformanceMetrics.calculateVolatility(portfolioValues);

        assertNotNull(volatility);
        assertTrue(volatility.compareTo(new BigDecimal("10")) > 0, "Should have high volatility");
    }

    @Test
    void testCalculateVolatility_InsufficientData() {
        List<BigDecimal> portfolioValues = List.of(new BigDecimal("10000"));

        BigDecimal volatility = PerformanceMetrics.calculateVolatility(portfolioValues);

        assertEquals(BigDecimal.ZERO, volatility);
    }

    @Test
    void testCalculateSortinoRatio_OnlyPositiveReturns() {
        List<BigDecimal> portfolioValues = Arrays.asList(
                new BigDecimal("10000"),
                new BigDecimal("10100"),
                new BigDecimal("10200"),
                new BigDecimal("10300"));

        BigDecimal sortinoRatio = PerformanceMetrics.calculateSortinoRatio(portfolioValues);

        assertNotNull(sortinoRatio);
        // Should be very high since no downside
        assertTrue(sortinoRatio.compareTo(new BigDecimal("100")) > 0, "Sortino should be high with no downside");
    }

    @Test
    void testCalculateSortinoRatio_WithDownside() {
        List<BigDecimal> portfolioValues = Arrays.asList(
                new BigDecimal("10000"),
                new BigDecimal("10500"),
                new BigDecimal("9800"),
                new BigDecimal("10300"),
                new BigDecimal("9900"));

        BigDecimal sortinoRatio = PerformanceMetrics.calculateSortinoRatio(portfolioValues);

        assertNotNull(sortinoRatio);
        // Should be reasonable but lower due to downside
    }

    @Test
    void testCalculateSortinoRatio_InsufficientData() {
        List<BigDecimal> portfolioValues = List.of(new BigDecimal("10000"));

        BigDecimal sortinoRatio = PerformanceMetrics.calculateSortinoRatio(portfolioValues);

        assertEquals(BigDecimal.ZERO, sortinoRatio);
    }

    private Trade createTrade(Trade.TradeType type, String price, int quantity) {
        return Trade.builder()
                .date(LocalDate.now())
                .symbol("AAPL")
                .type(type)
                .price(new BigDecimal(price))
                .quantity(quantity)
                .commission(BigDecimal.ZERO)
                .build();
    }
}
