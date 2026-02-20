package com.quantbacktest.backtester.domain;

/**
 * Strategy interface for implementing trading strategies.
 * Strategies receive market data ticks and make trading decisions.
 */
public interface Strategy {

    /**
     * Called for each market data point in chronological order.
     *
     * @param data      the current market data
     * @param portfolio the current portfolio state
     */
    void onTick(MarketData data, Portfolio portfolio);

    /**
     * Called after all data has been processed.
     *
     * @param portfolio the final portfolio state
     */
    void onFinish(Portfolio portfolio);

    /**
     * Get the strategy name.
     */
    String getName();
}
