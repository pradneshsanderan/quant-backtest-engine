package com.quantbacktest.backtester.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single market data point (OHLCV data).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketData {

    private LocalDate date;
    private String symbol;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;
}
