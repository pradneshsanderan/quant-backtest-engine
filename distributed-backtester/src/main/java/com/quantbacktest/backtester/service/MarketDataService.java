package com.quantbacktest.backtester.service;

import com.quantbacktest.backtester.domain.HistoricalMarketData;
import com.quantbacktest.backtester.domain.MarketData;
import com.quantbacktest.backtester.repository.HistoricalMarketDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Service for loading and managing market data.
 * Loads from PostgreSQL database with Redis caching.
 * Falls back to synthetic data if no real data is available.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {

    private final HistoricalMarketDataRepository historicalMarketDataRepository;

    /**
     * Load market data for the given symbol and date range.
     * Uses Redis cache (TTL: 10 minutes) to avoid repeated database queries.
     * Falls back to synthetic data if no historical data exists.
     */
    @Cacheable(value = "marketData", key = "#symbol + '_' + #startDate + '_' + #endDate")
    public List<MarketData> loadMarketData(String symbol, LocalDate startDate, LocalDate endDate) {
        log.info("Loading market data for {} from {} to {}", symbol, startDate, endDate);

        // Try to load from database first
        List<HistoricalMarketData> historicalData = historicalMarketDataRepository
                .findBySymbolAndDateRange(symbol, startDate, endDate);

        if (!historicalData.isEmpty()) {
            log.info("Loaded {} historical data points for {} from database", historicalData.size(), symbol);
            return historicalData.stream()
                    .map(HistoricalMarketData::toMarketData)
                    .collect(Collectors.toList());
        }

        // Fall back to synthetic data if no historical data exists
        log.warn("No historical data found for {}. Generating synthetic data.", symbol);
        List<MarketData> syntheticData = generateSyntheticData(symbol, startDate, endDate);

        log.info("Generated {} synthetic data points for {}", syntheticData.size(), symbol);
        return syntheticData;
    }

    /**
     * Generate synthetic market data for demonstration or testing.
     * Simulates realistic price movements with trends and volatility.
     */
    private List<MarketData> generateSyntheticData(String symbol, LocalDate startDate, LocalDate endDate) {
        List<MarketData> data = new ArrayList<>();
        Random random = new Random(42); // Fixed seed for deterministic results

        BigDecimal basePrice = new BigDecimal("100.00");
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            // Skip weekends
            if (currentDate.getDayOfWeek().getValue() <= 5) {
                // Generate price with random walk and trend
                double changePercent = (random.nextGaussian() * 0.02) + 0.0003; // 2% volatility, 0.03% daily trend
                BigDecimal change = basePrice.multiply(BigDecimal.valueOf(changePercent));
                basePrice = basePrice.add(change);

                // Ensure price stays positive
                if (basePrice.compareTo(BigDecimal.ONE) < 0) {
                    basePrice = BigDecimal.ONE;
                }

                // Generate OHLC data
                BigDecimal open = basePrice;
                BigDecimal high = basePrice.multiply(BigDecimal.valueOf(1 + Math.abs(random.nextGaussian()) * 0.01));
                BigDecimal low = basePrice.multiply(BigDecimal.valueOf(1 - Math.abs(random.nextGaussian()) * 0.01));
                BigDecimal close = basePrice.multiply(BigDecimal.valueOf(1 + (random.nextGaussian() * 0.005)));

                MarketData marketData = MarketData.builder()
                        .date(currentDate)
                        .symbol(symbol)
                        .open(open.setScale(2, RoundingMode.HALF_UP))
                        .high(high.setScale(2, RoundingMode.HALF_UP))
                        .low(low.setScale(2, RoundingMode.HALF_UP))
                        .close(close.setScale(2, RoundingMode.HALF_UP))
                        .volume((long) (1000000 + random.nextInt(500000)))
                        .build();

                data.add(marketData);
            }

            currentDate = currentDate.plusDays(1);
        }

        return data;
    }
}
