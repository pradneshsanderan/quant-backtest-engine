package com.quantbacktest.backtester.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Trade entity.
 */
class TradeTest {

    @Test
    void testTradeBuilder() {
        // Act
        Trade trade = Trade.builder()
                .date(LocalDate.of(2024, 1, 1))
                .symbol("AAPL")
                .type(Trade.TradeType.BUY)
                .price(new BigDecimal("100.00"))
                .quantity(50)
                .commission(new BigDecimal("5.00"))
                .build();

        // Assert
        assertNotNull(trade);
        assertEquals(LocalDate.of(2024, 1, 1), trade.getDate());
        assertEquals("AAPL", trade.getSymbol());
        assertEquals(Trade.TradeType.BUY, trade.getType());
        assertEquals(new BigDecimal("100.00"), trade.getPrice());
        assertEquals(50, trade.getQuantity());
        assertEquals(new BigDecimal("5.00"), trade.getCommission());
    }

    @Test
    void testGetTotalValue_BuyTrade() {
        // Arrange
        Trade trade = Trade.builder()
                .price(new BigDecimal("100.00"))
                .quantity(50)
                .commission(new BigDecimal("5.00"))
                .build();

        // Act
        BigDecimal totalValue = trade.getTotalValue();

        // Assert - (100 * 50) + 5 = 5005
        assertEquals(new BigDecimal("5005.00"), totalValue);
    }

    @Test
    void testGetTotalValue_SellTrade() {
        // Arrange
        Trade trade = Trade.builder()
                .price(new BigDecimal("110.00"))
                .quantity(30)
                .commission(new BigDecimal("3.00"))
                .build();

        // Act
        BigDecimal totalValue = trade.getTotalValue();

        // Assert - (110 * 30) + 3 = 3303
        assertEquals(new BigDecimal("3303.00"), totalValue);
    }

    @Test
    void testGetTotalValue_NoCommission() {
        // Arrange
        Trade trade = Trade.builder()
                .price(new BigDecimal("50.00"))
                .quantity(100)
                .commission(BigDecimal.ZERO)
                .build();

        // Act
        BigDecimal totalValue = trade.getTotalValue();

        // Assert - 50 * 100 = 5000
        assertEquals(new BigDecimal("5000.00"), totalValue);
    }

    @Test
    void testTradeTypeEnum() {
        assertEquals("BUY", Trade.TradeType.BUY.name());
        assertEquals("SELL", Trade.TradeType.SELL.name());
        assertEquals(2, Trade.TradeType.values().length);
    }

    @Test
    void testTradeEquality() {
        Trade trade1 = Trade.builder()
                .date(LocalDate.of(2024, 1, 1))
                .symbol("AAPL")
                .type(Trade.TradeType.BUY)
                .price(new BigDecimal("100.00"))
                .quantity(50)
                .commission(BigDecimal.ZERO)
                .build();

        Trade trade2 = Trade.builder()
                .date(LocalDate.of(2024, 1, 1))
                .symbol("AAPL")
                .type(Trade.TradeType.BUY)
                .price(new BigDecimal("100.00"))
                .quantity(50)
                .commission(BigDecimal.ZERO)
                .build();

        assertEquals(trade1, trade2);
        assertEquals(trade1.hashCode(), trade2.hashCode());
    }

    @Test
    void testSettersAndGetters() {
        Trade trade = new Trade();

        LocalDate date = LocalDate.of(2024, 1, 1);
        trade.setDate(date);
        trade.setSymbol("TSLA");
        trade.setType(Trade.TradeType.SELL);
        trade.setPrice(new BigDecimal("250.00"));
        trade.setQuantity(25);
        trade.setCommission(new BigDecimal("2.50"));

        assertEquals(date, trade.getDate());
        assertEquals("TSLA", trade.getSymbol());
        assertEquals(Trade.TradeType.SELL, trade.getType());
        assertEquals(new BigDecimal("250.00"), trade.getPrice());
        assertEquals(25, trade.getQuantity());
        assertEquals(new BigDecimal("2.50"), trade.getCommission());
    }
}
