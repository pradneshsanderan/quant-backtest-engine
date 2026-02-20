package com.quantbacktest.backtester.service;

import com.quantbacktest.backtester.domain.MarketData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Service for loading and managing market data.
 * Currently generates synthetic data; can be extended to load from CSV.
 */
@Service
@Slf4j
public class MarketDataService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Load market data for the given symbol and date range.
     * Currently generates synthetic data for demonstration.
     * TODO: Implement CSV loading from file system or database.
     */
    public List<MarketData> loadMarketData(String symbol, LocalDate startDate, LocalDate endDate) {
        log.info("Loading market data for {} from {} to {}", symbol, startDate, endDate);

        // Generate synthetic market data
        List<MarketData> data = generateSyntheticData(symbol, startDate, endDate);

        // Sort by date to ensure chronological order
        data.sort(Comparator.comparing(MarketData::getDate));

        log.info("Loaded {} data points for {}", data.size(), symbol);
        return data;
    }

    /**
     * Generate synthetic market data for demonstration.
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

    /**
     * Parse market data from CSV content.
     * Expected format: date,open,high,low,close,volume
     */
    public List<MarketData> parseCSV(String symbol, String csvContent) {
        List<MarketData> data = new ArrayList<>();
        String[] lines = csvContent.split("\n");

        // Skip header if present
        int startIndex = lines[0].toLowerCase().contains("date") ? 1 : 0;

        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty())
                continue;

            try {
                String[] parts = line.split(",");
                MarketData marketData = MarketData.builder()
                        .date(LocalDate.parse(parts[0], DATE_FORMATTER))
                        .symbol(symbol)
                        .open(new BigDecimal(parts[1]))
                        .high(new BigDecimal(parts[2]))
                        .low(new BigDecimal(parts[3]))
                        .close(new BigDecimal(parts[4]))
                        .volume(Long.parseLong(parts[5]))
                        .build();

                data.add(marketData);
            } catch (Exception e) {
                log.warn("Failed to parse CSV line {}: {}", i, line, e);
            }
        }

        return data;
    }
}
