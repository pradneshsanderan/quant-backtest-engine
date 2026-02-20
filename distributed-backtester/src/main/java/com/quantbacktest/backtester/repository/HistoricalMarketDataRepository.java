package com.quantbacktest.backtester.repository;

import com.quantbacktest.backtester.domain.HistoricalMarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for accessing historical market data from the database.
 */
@Repository
public interface HistoricalMarketDataRepository extends JpaRepository<HistoricalMarketData, Long> {

    /**
     * Find market data for a symbol within a date range, ordered by date.
     */
    @Query("SELECT h FROM HistoricalMarketData h WHERE h.symbol = :symbol " +
            "AND h.date >= :startDate AND h.date <= :endDate ORDER BY h.date ASC")
    List<HistoricalMarketData> findBySymbolAndDateRange(
            @Param("symbol") String symbol,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Check if data exists for a symbol and date.
     */
    boolean existsBySymbolAndDate(String symbol, LocalDate date);

    /**
     * Count records for a symbol within a date range.
     */
    @Query("SELECT COUNT(h) FROM HistoricalMarketData h WHERE h.symbol = :symbol " +
            "AND h.date >= :startDate AND h.date <= :endDate")
    long countBySymbolAndDateRange(
            @Param("symbol") String symbol,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Delete all data for a specific symbol.
     */
    void deleteBySymbol(String symbol);
}
