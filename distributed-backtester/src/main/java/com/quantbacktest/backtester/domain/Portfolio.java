package com.quantbacktest.backtester.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the current portfolio position and trading history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Portfolio {

    @Builder.Default
    private BigDecimal cash = BigDecimal.ZERO;

    @Builder.Default
    private int shares = 0;

    @Builder.Default
    private List<Trade> trades = new ArrayList<>();

    private BigDecimal initialCapital;

    /**
     * Execute a buy order.
     */
    public void buy(MarketData data, int quantity) {
        BigDecimal cost = data.getClose().multiply(BigDecimal.valueOf(quantity));
        if (cash.compareTo(cost) >= 0) {
            Trade trade = Trade.builder()
                    .date(data.getDate())
                    .symbol(data.getSymbol())
                    .type(Trade.TradeType.BUY)
                    .price(data.getClose())
                    .quantity(quantity)
                    .commission(BigDecimal.ZERO)
                    .build();

            cash = cash.subtract(cost);
            shares += quantity;
            trades.add(trade);
        }
    }

    /**
     * Execute a sell order.
     */
    public void sell(MarketData data, int quantity) {
        if (shares >= quantity) {
            BigDecimal proceeds = data.getClose().multiply(BigDecimal.valueOf(quantity));

            Trade trade = Trade.builder()
                    .date(data.getDate())
                    .symbol(data.getSymbol())
                    .type(Trade.TradeType.SELL)
                    .price(data.getClose())
                    .quantity(quantity)
                    .commission(BigDecimal.ZERO)
                    .build();

            cash = cash.add(proceeds);
            shares -= quantity;
            trades.add(trade);
        }
    }

    /**
     * Get current portfolio value.
     */
    public BigDecimal getPortfolioValue(BigDecimal currentPrice) {
        BigDecimal stockValue = currentPrice.multiply(BigDecimal.valueOf(shares));
        return cash.add(stockValue);
    }

    /**
     * Calculate total return percentage.
     */
    public BigDecimal getTotalReturn(BigDecimal finalValue) {
        if (initialCapital.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return finalValue.subtract(initialCapital)
                .divide(initialCapital, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
