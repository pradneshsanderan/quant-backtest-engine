package com.quantbacktest.backtester.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BuyAndHoldStrategy.
 */
class BuyAndHoldStrategyTest {

    private BuyAndHoldStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new BuyAndHoldStrategy();
        portfolio = Portfolio.builder()
                .cash(new BigDecimal("10000.00"))
                .shares(0)
                .trades(new ArrayList<>())
                .initialCapital(new BigDecimal("10000.00"))
                .build();
    }

    @Test
    void testBuysOnFirstTick() {
        // Arrange
        MarketData marketData = MarketData.builder()
                .date(LocalDate.of(2024, 1, 1))
                .symbol("AAPL")
                .close(new BigDecimal("100.00"))
                .build();

        // Act
        strategy.onTick(marketData, portfolio);

        // Assert
        assertEquals(100, portfolio.getShares(), "Should buy 100 shares with $10,000 at $100/share");
        assertEquals(new BigDecimal("0.00"), portfolio.getCash(), "Should have $0 cash remaining");
        assertEquals(1, portfolio.getTrades().size(), "Should have one trade recorded");

        Trade trade = portfolio.getTrades().get(0);
        assertEquals(Trade.TradeType.BUY, trade.getType());
        assertEquals(100, trade.getQuantity());
        assertEquals(new BigDecimal("100.00"), trade.getPrice());
    }

    @Test
    void testDoesNotBuyOnSubsequentTicks() {
        // Arrange
        MarketData firstTick = MarketData.builder()
                .date(LocalDate.of(2024, 1, 1))
                .symbol("AAPL")
                .close(new BigDecimal("100.00"))
                .build();

        MarketData secondTick = MarketData.builder()
                .date(LocalDate.of(2024, 1, 2))
                .symbol("AAPL")
                .close(new BigDecimal("110.00"))
                .build();

        // Act
        strategy.onTick(firstTick, portfolio);
        int sharesAfterFirstTick = portfolio.getShares();
        strategy.onTick(secondTick, portfolio);

        // Assert
        assertEquals(sharesAfterFirstTick, portfolio.getShares(), "Should not buy more shares on second tick");
        assertEquals(1, portfolio.getTrades().size(), "Should still have only one trade");
    }

    @Test
    void testDoesNotBuyWithInsufficientFunds() {
        // Arrange
        portfolio.setCash(new BigDecimal("50.00"));
        MarketData marketData = MarketData.builder()
                .date(LocalDate.of(2024, 1, 1))
                .symbol("AAPL")
                .close(new BigDecimal("100.00"))
                .build();

        // Act
        strategy.onTick(marketData, portfolio);

        // Assert
        assertEquals(0, portfolio.getShares(), "Should not buy any shares with insufficient funds");
        assertEquals(new BigDecimal("50.00"), portfolio.getCash(), "Cash should remain unchanged");
        assertEquals(0, portfolio.getTrades().size(), "Should have no trades");
    }

    @Test
    void testBuysPartialSharesWithExactAmount() {
        // Arrange
        portfolio.setCash(new BigDecimal("150.00"));
        MarketData marketData = MarketData.builder()
                .date(LocalDate.of(2024, 1, 1))
                .symbol("AAPL")
                .close(new BigDecimal("100.00"))
                .build();

        // Act
        strategy.onTick(marketData, portfolio);

        // Assert
        assertEquals(1, portfolio.getShares(), "Should buy 1 share (150/100 = 1 rounded down)");
        assertEquals(new BigDecimal("50.00"), portfolio.getCash(), "Should have $50 remaining");
    }

    @Test
    void testStrategyName() {
        assertEquals("BuyAndHold", strategy.getName());
    }

    @Test
    void testOnFinishDoesNotCrash() {
        // Arrange
        MarketData marketData = MarketData.builder()
                .date(LocalDate.of(2024, 1, 1))
                .symbol("AAPL")
                .close(new BigDecimal("100.00"))
                .build();
        strategy.onTick(marketData, portfolio);

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> strategy.onFinish(portfolio));
    }

    @Test
    void testMultipleTicksWithNoCash() {
        // Arrange
        portfolio.setCash(BigDecimal.ZERO);
        MarketData marketData = MarketData.builder()
                .date(LocalDate.of(2024, 1, 1))
                .symbol("AAPL")
                .close(new BigDecimal("100.00"))
                .build();

        // Act
        strategy.onTick(marketData, portfolio);
        strategy.onTick(marketData, portfolio);

        // Assert
        assertEquals(0, portfolio.getShares(), "Should not buy with zero cash");
        assertEquals(0, portfolio.getTrades().size(), "Should have no trades");
    }
}
