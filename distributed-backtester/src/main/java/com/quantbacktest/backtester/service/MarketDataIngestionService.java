package com.quantbacktest.backtester.service;

import com.quantbacktest.backtester.domain.HistoricalMarketData;
import com.quantbacktest.backtester.domain.MarketData;
import com.quantbacktest.backtester.repository.HistoricalMarketDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for ingesting CSV market data into the database.
 * Supports Yahoo Finance CSV format.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataIngestionService {

    private final HistoricalMarketDataRepository historicalMarketDataRepository;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    };

    /**
     * Ingest CSV data from an input stream.
     * Expected format: Date,Open,High,Low,Close,Volume
     * 
     * @param symbol      The stock symbol
     * @param inputStream The CSV input stream
     * @return Number of records inserted
     */
    @Transactional
    public int ingestCSV(String symbol, InputStream inputStream) throws IOException {
        log.info("Starting CSV ingestion for symbol: {}", symbol);

        List<HistoricalMarketData> dataToInsert = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Skip header row
                if (isFirstLine) {
                    isFirstLine = false;
                    if (line.toLowerCase().contains("date")) {
                        continue;
                    }
                }

                try {
                    HistoricalMarketData data = parseCSVLine(symbol, line);
                    if (data != null) {
                        dataToInsert.add(data);
                    }

                    // Batch insert every 1000 records for efficiency
                    if (dataToInsert.size() >= 1000) {
                        historicalMarketDataRepository.saveAll(dataToInsert);
                        log.info("Batch inserted {} records for {}", dataToInsert.size(), symbol);
                        dataToInsert.clear();
                    }

                } catch (Exception e) {
                    log.warn("Failed to parse CSV line: {} - Error: {}", line, e.getMessage());
                }
            }

            // Insert remaining records
            if (!dataToInsert.isEmpty()) {
                historicalMarketDataRepository.saveAll(dataToInsert);
                log.info("Inserted final batch of {} records for {}", dataToInsert.size(), symbol);
            }
        }

        long totalRecords = historicalMarketDataRepository.countBySymbolAndDateRange(
                symbol, LocalDate.of(1900, 1, 1), LocalDate.now());

        log.info("CSV ingestion completed for {}. Total records in DB: {}", symbol, totalRecords);
        return (int) totalRecords;
    }

    /**
     * Parse a single CSV line into HistoricalMarketData.
     * Supports both Yahoo Finance and generic CSV formats.
     */
    private HistoricalMarketData parseCSVLine(String symbol, String line) {
        String[] parts = line.split(",");

        if (parts.length < 6) {
            log.warn("Invalid CSV line format (expected 6+ columns): {}", line);
            return null;
        }

        try {
            LocalDate date = parseDate(parts[0].trim());
            BigDecimal open = new BigDecimal(parts[1].trim());
            BigDecimal high = new BigDecimal(parts[2].trim());
            BigDecimal low = new BigDecimal(parts[3].trim());
            BigDecimal close = new BigDecimal(parts[4].trim());
            Long volume = Long.parseLong(parts[5].trim().replace(",", ""));

            return HistoricalMarketData.builder()
                    .symbol(symbol)
                    .date(date)
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse values from line: {} - Error: {}", line, e.getMessage());
            return null;
        }
    }

    /**
     * Parse date with multiple format support.
     */
    private LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception e) {
                // Try next formatter
            }
        }
        throw new IllegalArgumentException("Unable to parse date: " + dateStr);
    }

    /**
     * Ingest CSV from string content.
     */
    @Transactional
    public int ingestCSVFromString(String symbol, String csvContent) {
        log.info("Starting CSV string ingestion for symbol: {}", symbol);

        List<HistoricalMarketData> dataToInsert = new ArrayList<>();
        String[] lines = csvContent.split("\n");

        boolean isFirstLine = true;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // Skip header row
            if (isFirstLine) {
                isFirstLine = false;
                if (line.toLowerCase().contains("date")) {
                    continue;
                }
            }

            try {
                HistoricalMarketData data = parseCSVLine(symbol, line);
                if (data != null) {
                    dataToInsert.add(data);
                }

                // Batch insert every 1000 records
                if (dataToInsert.size() >= 1000) {
                    historicalMarketDataRepository.saveAll(dataToInsert);
                    log.info("Batch inserted {} records for {}", dataToInsert.size(), symbol);
                    dataToInsert.clear();
                }

            } catch (Exception e) {
                log.warn("Failed to parse CSV line: {} - Error: {}", line, e.getMessage());
            }
        }

        // Insert remaining records
        if (!dataToInsert.isEmpty()) {
            historicalMarketDataRepository.saveAll(dataToInsert);
            log.info("Inserted final batch of {} records for {}", dataToInsert.size(), symbol);
        }

        long totalRecords = historicalMarketDataRepository.countBySymbolAndDateRange(
                symbol, LocalDate.of(1900, 1, 1), LocalDate.now());

        log.info("CSV string ingestion completed for {}. Total records in DB: {}", symbol, totalRecords);
        return (int) totalRecords;
    }

    /**
     * Delete all data for a symbol (useful for reloading).
     */
    @Transactional
    public void deleteSymbolData(String symbol) {
        log.info("Deleting all data for symbol: {}", symbol);
        historicalMarketDataRepository.deleteBySymbol(symbol);
        log.info("Deleted all data for symbol: {}", symbol);
    }
}
