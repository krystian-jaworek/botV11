package com.tradingbot.backtest.data;

import com.tradingbot.core.algorithms.MarketDataProvider;
import com.tradingbot.core.models.Candle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Market data provider for backtesting.
 * Provides candles from pre-loaded historical data.
 */
public class BacktestMarketDataProvider implements MarketDataProvider {

    private final List<Candle> allCandles;
    private int currentIndex;

    public BacktestMarketDataProvider(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("Candles list cannot be null or empty");
        }
        this.allCandles = new ArrayList<>(candles);
        this.currentIndex = 0;
    }

    @Override
    public Candle getCurrentCandle() {
        if (currentIndex >= allCandles.size()) {
            throw new IllegalStateException("No more candles available");
        }
        return allCandles.get(currentIndex);
    }

    @Override
    public List<Candle> getHistoricalCandles(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        // Return candles from (currentIndex - count + 1) to currentIndex (inclusive)
        int startIndex = Math.max(0, currentIndex - count + 1);
        int endIndex = Math.min(currentIndex + 1, allCandles.size());

        return new ArrayList<>(allCandles.subList(startIndex, endIndex));
    }

    @Override
    public boolean hasNext() {
        return currentIndex < allCandles.size() - 1;
    }

    @Override
    public void moveNext() {
        if (!hasNext()) {
            throw new IllegalStateException("No more candles available");
        }
        currentIndex++;
    }

    /**
     * Get total number of candles
     */
    public int getTotalCandles() {
        return allCandles.size();
    }

    /**
     * Get current index
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Reset to first candle
     */
    public void reset() {
        this.currentIndex = 0;
    }
}
