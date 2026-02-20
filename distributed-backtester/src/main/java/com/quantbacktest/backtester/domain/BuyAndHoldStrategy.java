package com.quantbacktest.backtester.domain;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Simple buy-and-hold strategy.
 * Buys as many shares as possible on the first tick and holds until the end.
 */
@Slf4j
public class BuyAndHoldStrategy implements Strategy {

    private boolean hasBought = false;

    @Override
    public void onTick(MarketData data, Portfolio portfolio) {
        if (!hasBought && portfolio.getCash().compareTo(BigDecimal.ZERO) > 0) {
            // Buy as many shares as possible
            int sharesToBuy = portfolio.getCash()
                    .divide(data.getClose(), 0, RoundingMode.DOWN)
                    .intValue();

            if (sharesToBuy > 0) {
                portfolio.buy(data, sharesToBuy);
                hasBought = true;
                log.debug("Buy and Hold: Bought {} shares at {} on {}",
                        sharesToBuy, data.getClose(), data.getDate());
            }
        }
    }

    @Override
    public void onFinish(Portfolio portfolio) {
        log.debug("Buy and Hold strategy completed. Final shares: {}, Final cash: {}",
                portfolio.getShares(), portfolio.getCash());
    }

    @Override
    public String getName() {
        return "BuyAndHold";
    }
}
