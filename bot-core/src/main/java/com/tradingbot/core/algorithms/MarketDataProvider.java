package com.tradingbot.core.algorithms;

import com.tradingbot.core.models.Candle;

import java.util.List;

/**
 * Interface for providing market data to algorithms.
 * Implementations differ between backtesting (file-based) and production (API-based).
 */
public interface MarketDataProvider {

    /**
     * Get the current/latest candle
     */
    Candle getCurrentCandle();

    /**
     * Get historical candles.
     * Useful if algorithm needs to calculate moving averages, etc.
     *
     * @param count Number of historical candles to retrieve (including current)
     * @return List of candles, most recent last
     */
    List<Candle> getHistoricalCandles(int count);

    /**
     * Check if more candles are available (used in backtesting)
     */
    boolean hasNext();

    /**
     * Move to next candle (used in backtesting)
     */
    void moveNext();
}
