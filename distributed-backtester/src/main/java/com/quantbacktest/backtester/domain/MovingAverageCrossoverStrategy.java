package com.quantbacktest.backtester.domain;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Moving Average Crossover Strategy.
 * Buys when short MA crosses above long MA, sells when short MA crosses below
 * long MA.
 */
@Slf4j
public class MovingAverageCrossoverStrategy implements Strategy {

    private final int shortPeriod;
    private final int longPeriod;

    private final Queue<BigDecimal> shortWindow = new LinkedList<>();
    private final Queue<BigDecimal> longWindow = new LinkedList<>();

    private BigDecimal previousShortMA = null;
    private BigDecimal previousLongMA = null;

    public MovingAverageCrossoverStrategy(int shortPeriod, int longPeriod) {
        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException("Short period must be less than long period");
        }
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
    }

    @Override
    public void onTick(MarketData data, Portfolio portfolio) {
        BigDecimal closePrice = data.getClose();

        // Update windows
        shortWindow.add(closePrice);
        longWindow.add(closePrice);

        if (shortWindow.size() > shortPeriod) {
            shortWindow.poll();
        }
        if (longWindow.size() > longPeriod) {
            longWindow.poll();
        }

        // Wait until we have enough data
        if (longWindow.size() < longPeriod) {
            return;
        }

        // Calculate moving averages
        BigDecimal shortMA = calculateMA(shortWindow);
        BigDecimal longMA = calculateMA(longWindow);

        // Check for crossover
        if (previousShortMA != null && previousLongMA != null) {
            boolean wasBelowLong = previousShortMA.compareTo(previousLongMA) < 0;
            boolean isAboveLong = shortMA.compareTo(longMA) > 0;
            boolean wasAboveLong = previousShortMA.compareTo(previousLongMA) > 0;
            boolean isBelowLong = shortMA.compareTo(longMA) < 0;

            // Golden cross - buy signal
            if (wasBelowLong && isAboveLong) {
                int sharesToBuy = portfolio.getCash()
                        .divide(closePrice, 0, RoundingMode.DOWN)
                        .intValue();

                if (sharesToBuy > 0) {
                    portfolio.buy(data, sharesToBuy);
                    log.debug("MA Crossover: BUY {} shares at {} on {} (Short MA: {}, Long MA: {})",
                            sharesToBuy, closePrice, data.getDate(), shortMA, longMA);
                }
            }
            // Death cross - sell signal
            else if (wasAboveLong && isBelowLong) {
                int sharesToSell = portfolio.getShares();

                if (sharesToSell > 0) {
                    portfolio.sell(data, sharesToSell);
                    log.debug("MA Crossover: SELL {} shares at {} on {} (Short MA: {}, Long MA: {})",
                            sharesToSell, closePrice, data.getDate(), shortMA, longMA);
                }
            }
        }

        previousShortMA = shortMA;
        previousLongMA = longMA;
    }

    @Override
    public void onFinish(Portfolio portfolio) {
        // Sell all remaining shares at the end
        log.debug("MA Crossover strategy completed. Final shares: {}, Final cash: {}",
                portfolio.getShares(), portfolio.getCash());
    }

    @Override
    public String getName() {
        return "MovingAverageCrossover(" + shortPeriod + "," + longPeriod + ")";
    }

    private BigDecimal calculateMA(Queue<BigDecimal> window) {
        BigDecimal sum = window.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(window.size()), 4, RoundingMode.HALF_UP);
    }
}
