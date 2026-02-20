package com.quantbacktest.backtester.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity representing historical market data stored in the database.
 * This is the persistent version of MarketData for PostgreSQL storage.
 */
@Entity
@Table(name = "historical_market_data", uniqueConstraints = {
        @UniqueConstraint(name = "uk_symbol_date", columnNames = { "symbol", "date" })
}, indexes = {
        @Index(name = "idx_symbol_date", columnList = "symbol, date"),
        @Index(name = "idx_symbol", columnList = "symbol")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalMarketData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "open", nullable = false, precision = 12, scale = 4)
    private BigDecimal open;

    @Column(name = "high", nullable = false, precision = 12, scale = 4)
    private BigDecimal high;

    @Column(name = "low", nullable = false, precision = 12, scale = 4)
    private BigDecimal low;

    @Column(name = "close", nullable = false, precision = 12, scale = 4)
    private BigDecimal close;

    @Column(name = "volume", nullable = false)
    private Long volume;

    /**
     * Convert entity to domain MarketData object.
     */
    public MarketData toMarketData() {
        return MarketData.builder()
                .date(this.date)
                .symbol(this.symbol)
                .open(this.open)
                .high(this.high)
                .low(this.low)
                .close(this.close)
                .volume(this.volume)
                .build();
    }

    /**
     * Create entity from domain MarketData object.
     */
    public static HistoricalMarketData fromMarketData(MarketData data) {
        return HistoricalMarketData.builder()
                .symbol(data.getSymbol())
                .date(data.getDate())
                .open(data.getOpen())
                .high(data.getHigh())
                .low(data.getLow())
                .close(data.getClose())
                .volume(data.getVolume())
                .build();
    }
}
