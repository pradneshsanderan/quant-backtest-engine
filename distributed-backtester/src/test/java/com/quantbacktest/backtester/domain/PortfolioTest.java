package com.quantbacktest.backtester.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Portfolio operations.
 */
class PortfolioTest {

    private Portfolio portfolio;
    private MarketData marketData;

    @BeforeEach
    void setUp() {
        portfolio = Portfolio.builder()
                .cash(new BigDecimal("10000.00"))
                .shares(0)
                .trades(new ArrayList<>())
                .initialCapital(new BigDecimal("10000.00"))
                .build();

        marketData = MarketData.builder()
                .date(LocalDate.of(2024, 1, 1))
                .symbol("AAPL")
                .open(new BigDecimal("98.00"))
                .high(new BigDecimal("102.00"))
                .low(new BigDecimal("97.00"))
                .close(new BigDecimal("100.00"))
                .volume(1000000L)
                .build();
    }

    @Test
    void testBuy_SuccessfulPurchase() {
        // Act
        portfolio.buy(marketData, 50);

        // Assert
        assertEquals(50, portfolio.getShares(), "Should have 50 shares");
        assertEquals(new BigDecimal("5000.00"), portfolio.getCash(), "Should have $5000 remaining");
        assertEquals(1, portfolio.getTrades().size(), "Should have one trade recorded");

        Trade trade = portfolio.getTrades().get(0);
        assertEquals(Trade.TradeType.BUY, trade.getType());
        assertEquals(50, trade.getQuantity());
        assertEquals(new BigDecimal("100.00"), trade.getPrice());
        assertEquals("AAPL", trade.getSymbol());
    }

    @Test
    void testBuy_InsufficientFunds() {
        // Act - Try to buy 150 shares at $100 each ($15,000 total) with only $10,000
        portfolio.buy(marketData, 150);

        // Assert - Trade should not execute
        assertEquals(0, portfolio.getShares(), "Should have no shares purchased");
        assertEquals(new BigDecimal("10000.00"), portfolio.getCash(), "Cash should remain unchanged");
        assertEquals(0, portfolio.getTrades().size(), "Should have no trades recorded");
    }

    @Test
    void testBuy_ExactAmount() {
        // Act - Buy exactly the amount we can afford
        portfolio.buy(marketData, 100);

        // Assert
        assertEquals(100, portfolio.getShares());
        assertEquals(new BigDecimal("0.00"), portfolio.getCash());
        assertEquals(1, portfolio.getTrades().size());
    }

    @Test
    void testBuy_MultiplePurchases() {
        // Act
        portfolio.buy(marketData, 20);
        portfolio.buy(marketData, 30);

        // Assert
        assertEquals(50, portfolio.getShares(), "Should have total of 50 shares");
        assertEquals(new BigDecimal("5000.00"), portfolio.getCash());
        assertEquals(2, portfolio.getTrades().size(), "Should have two trades recorded");
    }

    @Test
    void testSell_SuccessfulSale() {
        // Arrange - First buy some shares
        portfolio.buy(marketData, 50);

        // Act
        portfolio.sell(marketData, 30);

        // Assert
        assertEquals(20, portfolio.getShares(), "Should have 20 shares remaining");
        assertEquals(new BigDecimal("8000.00"), portfolio.getCash(), "Should have $8000 cash");
        assertEquals(2, portfolio.getTrades().size(), "Should have two trades (buy + sell)");

        Trade sellTrade = portfolio.getTrades().get(1);
        assertEquals(Trade.TradeType.SELL, sellTrade.getType());
        assertEquals(30, sellTrade.getQuantity());
    }

    @Test
    void testSell_InsufficientShares() {
        // Arrange - Buy 20 shares
        portfolio.buy(marketData, 20);
        BigDecimal cashBefore = portfolio.getCash();

        // Act - Try to sell 50 shares when we only have 20
        portfolio.sell(marketData, 50);

        // Assert - Sale should not execute
        assertEquals(20, portfolio.getShares(), "Should still have 20 shares");
        assertEquals(cashBefore, portfolio.getCash(), "Cash should remain unchanged");
        assertEquals(1, portfolio.getTrades().size(), "Should only have the buy trade");
    }

    @Test
    void testSell_AllShares() {
        // Arrange
        portfolio.buy(marketData, 50);

        // Act - Sell all shares
        portfolio.sell(marketData, 50);

        // Assert
        assertEquals(0, portfolio.getShares(), "Should have no shares");
        assertEquals(new BigDecimal("10000.00"), portfolio.getCash(), "Should have original capital back");
        assertEquals(2, portfolio.getTrades().size());
    }

    @Test
    void testGetPortfolioValue() {
        // Arrange - Buy 50 shares at $100
        portfolio.buy(marketData, 50);

        // Act
        BigDecimal currentPrice = new BigDecimal("120.00");
        BigDecimal portfolioValue = portfolio.getPortfolioValue(currentPrice);

        // Assert - (50 shares * $120) + $5000 cash = $11,000
        assertEquals(new BigDecimal("11000.00"), portfolioValue);
    }

    @Test
    void testGetPortfolioValue_NoShares() {
        // Act
        BigDecimal portfolioValue = portfolio.getPortfolioValue(new BigDecimal("100.00"));

        // Assert - Should just be the cash
        assertEquals(new BigDecimal("10000.00"), portfolioValue);
    }

    @Test
    void testGetPortfolioValue_NoCash() {
        // Arrange - Buy all shares
        portfolio.buy(marketData, 100);

        // Act
        BigDecimal portfolioValue = portfolio.getPortfolioValue(new BigDecimal("110.00"));

        // Assert - 100 shares * $110 = $11,000
        assertEquals(new BigDecimal("11000.00"), portfolioValue);
    }

    @Test
    void testGetTotalReturn_Profit() {
        // Act
        BigDecimal finalValue = new BigDecimal("12000.00");
        BigDecimal totalReturn = portfolio.getTotalReturn(finalValue);

        // Assert - (12000 - 10000) / 10000 * 100 = 20%
        assertEquals(new BigDecimal("20.0000"), totalReturn);
    }

    @Test
    void testGetTotalReturn_Loss() {
        // Act
        BigDecimal finalValue = new BigDecimal("8000.00");
        BigDecimal totalReturn = portfolio.getTotalReturn(finalValue);

        // Assert - (8000 - 10000) / 10000 * 100 = -20%
        assertEquals(new BigDecimal("-20.0000"), totalReturn);
    }

    @Test
    void testGetTotalReturn_NoChange() {
        // Act
        BigDecimal finalValue = new BigDecimal("10000.00");
        BigDecimal totalReturn = portfolio.getTotalReturn(finalValue);

        // Assert
        assertEquals(new BigDecimal("0.0000"), totalReturn);
    }

    @Test
    void testGetTotalReturn_ZeroInitialCapital() {
        // Arrange
        portfolio.setInitialCapital(BigDecimal.ZERO);

        // Act
        BigDecimal finalValue = new BigDecimal("1000.00");
        BigDecimal totalReturn = portfolio.getTotalReturn(finalValue);

        // Assert - Should handle division by zero
        assertEquals(BigDecimal.ZERO, totalReturn);
    }

    @Test
    void testTradeHistory_MaintainsOrder() {
        // Act
        portfolio.buy(marketData, 10);
        portfolio.sell(marketData, 5);
        portfolio.buy(marketData, 20);
        portfolio.sell(marketData, 15);

        // Assert
        assertEquals(4, portfolio.getTrades().size());
        assertEquals(Trade.TradeType.BUY, portfolio.getTrades().get(0).getType());
        assertEquals(Trade.TradeType.SELL, portfolio.getTrades().get(1).getType());
        assertEquals(Trade.TradeType.BUY, portfolio.getTrades().get(2).getType());
        assertEquals(Trade.TradeType.SELL, portfolio.getTrades().get(3).getType());

        // Final position: 10 - 5 + 20 - 15 = 10 shares
        assertEquals(10, portfolio.getShares());
    }
}
