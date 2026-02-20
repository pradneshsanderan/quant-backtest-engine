package com.quantbacktest.backtester.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a trade execution in the backtest.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

    private LocalDate date;
    private String symbol;
    private TradeType type;
    private BigDecimal price;
    private int quantity;
    private BigDecimal commission;

    public enum TradeType {
        BUY, SELL
    }

    /**
     * Calculate total cost/proceeds of the trade.
     */
    public BigDecimal getTotalValue() {
        return price.multiply(BigDecimal.valueOf(quantity)).add(commission);
    }
}
