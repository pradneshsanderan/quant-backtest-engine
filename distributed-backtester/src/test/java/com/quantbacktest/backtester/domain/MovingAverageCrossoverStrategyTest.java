package com.quantbacktest.backtester.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MovingAverageCrossoverStrategy.
 */
class MovingAverageCrossoverStrategyTest {

    @Test
    void testConstructorThrowsExceptionWhenShortPeriodGreaterOrEqualToLongPeriod() {
        assertThrows(IllegalArgumentException.class, () -> new MovingAverageCrossoverStrategy(50, 50));

        assertThrows(IllegalArgumentException.class, () -> new MovingAverageCrossoverStrategy(100, 50));
    }

    @Test
    void testConstructorAcceptsValidPeriods() {
        assertDoesNotThrow(() -> new MovingAverageCrossoverStrategy(10, 20));
        assertDoesNotThrow(() -> new MovingAverageCrossoverStrategy(1, 2));
    }

    @Test
    void testDoesNotTradeUntilEnoughDataPoints() {
        // Arrange
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy(5, 10);
        Portfolio portfolio = Portfolio.builder()
                .cash(new BigDecimal("10000.00"))
                .shares(0)
                .trades(new ArrayList<>())
                .initialCapital(new BigDecimal("10000.00"))
                .build();

        // Act - Send 9 ticks (less than long period of 10)
        for (int i = 1; i <= 9; i++) {
            MarketData data = createMarketData(i, "100.00");
            strategy.onTick(data, portfolio);
        }

        // Assert
        assertEquals(0, portfolio.getTrades().size(), "Should not trade before long period is satisfied");
    }

    @Test
    void testGoldenCrossBuySignal() {
        // Arrange
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy(2, 3);
        Portfolio portfolio = Portfolio.builder()
                .cash(new BigDecimal("10000.00"))
                .shares(0)
                .trades(new ArrayList<>())
                .initialCapital(new BigDecimal("10000.00"))
                .build();

        // Act - Create downtrend then uptrend to trigger golden cross
        // Downtrend: 110, 105, 100
        strategy.onTick(createMarketData(1, "110.00"), portfolio);
        strategy.onTick(createMarketData(2, "105.00"), portfolio);
        strategy.onTick(createMarketData(3, "100.00"), portfolio);

        int tradesBeforeCross = portfolio.getTrades().size();

        // Uptrend: 105, 115 (should trigger golden cross)
        strategy.onTick(createMarketData(4, "105.00"), portfolio);
        strategy.onTick(createMarketData(5, "115.00"), portfolio);

        // Assert
        assertTrue(portfolio.getTrades().size() > tradesBeforeCross, "Should have executed buy trade");
        assertEquals(Trade.TradeType.BUY, portfolio.getTrades().get(0).getType());
        assertTrue(portfolio.getShares() > 0, "Should have purchased shares");
    }

    @Test
    void testDeathCrossSellSignal() {
        // Arrange
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy(2, 3);
        Portfolio portfolio = Portfolio.builder()
                .cash(new BigDecimal("10000.00"))
                .shares(0)
                .trades(new ArrayList<>())
                .initialCapital(new BigDecimal("10000.00"))
                .build();

        // Act - First trigger golden cross to buy
        strategy.onTick(createMarketData(1, "90.00"), portfolio);
        strategy.onTick(createMarketData(2, "95.00"), portfolio);
        strategy.onTick(createMarketData(3, "100.00"), portfolio);
        strategy.onTick(createMarketData(4, "105.00"), portfolio);
        strategy.onTick(createMarketData(5, "110.00"), portfolio);

        int sharesOwned = portfolio.getShares();
        assertTrue(sharesOwned > 0, "Should own shares after golden cross");

        // Now trigger death cross with downtrend
        strategy.onTick(createMarketData(6, "108.00"), portfolio);
        strategy.onTick(createMarketData(7, "90.00"), portfolio);

        // Assert
        Trade lastTrade = portfolio.getTrades().get(portfolio.getTrades().size() - 1);
        assertEquals(Trade.TradeType.SELL, lastTrade.getType());
        assertEquals(0, portfolio.getShares(), "Should have sold all shares");
    }

    @Test
    void testStrategyName() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy(10, 20);
        assertEquals("MovingAverageCrossover(10,20)", strategy.getName());
    }

    @Test
    void testOnFinishDoesNotCrash() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy(5, 10);
        Portfolio portfolio = Portfolio.builder()
                .cash(new BigDecimal("10000.00"))
                .shares(0)
                .trades(new ArrayList<>())
                .build();

        assertDoesNotThrow(() -> strategy.onFinish(portfolio));
    }

    @Test
    void testDoesNotBuyWithoutSufficientCash() {
        // Arrange
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy(2, 3);
        Portfolio portfolio = Portfolio.builder()
                .cash(BigDecimal.ZERO)
                .shares(0)
                .trades(new ArrayList<>())
                .initialCapital(new BigDecimal("10000.00"))
                .build();

        // Act - Trigger golden cross but with no cash
        strategy.onTick(createMarketData(1, "90.00"), portfolio);
        strategy.onTick(createMarketData(2, "95.00"), portfolio);
        strategy.onTick(createMarketData(3, "100.00"), portfolio);
        strategy.onTick(createMarketData(4, "105.00"), portfolio);
        strategy.onTick(createMarketData(5, "110.00"), portfolio);

        // Assert
        assertEquals(0, portfolio.getTrades().size(), "Should not execute any trades without cash");
        assertEquals(0, portfolio.getShares(), "Should have no shares");
    }

    @Test
    void testDoesNotSellWithoutShares() {
        // Arrange
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy(2, 3);
        Portfolio portfolio = Portfolio.builder()
                .cash(new BigDecimal("10000.00"))
                .shares(0)
                .trades(new ArrayList<>())
                .initialCapital(new BigDecimal("10000.00"))
                .build();

        // Act - Trigger death cross without owning shares
        strategy.onTick(createMarketData(1, "110.00"), portfolio);
        strategy.onTick(createMarketData(2, "105.00"), portfolio);
        strategy.onTick(createMarketData(3, "100.00"), portfolio);
        strategy.onTick(createMarketData(4, "95.00"), portfolio);
        strategy.onTick(createMarketData(5, "90.00"), portfolio);

        // Assert - may have some trades but should not crash
        assertEquals(0, portfolio.getShares(), "Should have no shares to sell");
    }

    private MarketData createMarketData(int dayOffset, String closePrice) {
        return MarketData.builder()
                .date(LocalDate.of(2024, 1, 1).plusDays(dayOffset))
                .symbol("AAPL")
                .close(new BigDecimal(closePrice))
                .build();
    }
}
