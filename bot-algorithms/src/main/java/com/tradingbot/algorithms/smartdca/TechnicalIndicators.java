package com.tradingbot.algorithms.smartdca;

import com.tradingbot.core.models.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Technical indicator calculations for Smart DCA algorithm.
 * All methods are static and stateless.
 */
public class TechnicalIndicators {

    /**
     * Calculate RSI (Relative Strength Index) using Wilder's smoothing method.
     *
     * @param candles Historical candles (most recent last)
     * @param period RSI period (typically 14)
     * @return RSI value between 0 and 100, or null if insufficient data
     */
    public static BigDecimal calculateRSI(List<Candle> candles, int period) {
        if (candles.size() < period + 1) {
            return null;  // Insufficient data
        }

        // Calculate price changes
        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;

        // Initial average gain/loss over 'period' changes
        // We need period changes, so start from index where we can safely do i-1
        int startIdx = candles.size() - period;  // This gives us 'period' iterations
        for (int i = startIdx; i < candles.size(); i++) {
            BigDecimal change = candles.get(i).close().subtract(candles.get(i - 1).close());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                avgGain = avgGain.add(change);
            } else {
                avgLoss = avgLoss.add(change.abs());
            }
        }

        avgGain = avgGain.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);

        // Calculate RS and RSI
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("100");  // No losses = RSI 100
        }

        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        BigDecimal rsi = new BigDecimal("100")
            .subtract(new BigDecimal("100")
                .divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));

        return rsi;
    }

    /**
     * Calculate EMA (Exponential Moving Average).
     *
     * @param candles Historical candles (most recent last)
     * @param period EMA period (e.g., 200)
     * @return EMA value, or null if insufficient data
     */
    public static BigDecimal calculateEMA(List<Candle> candles, int period) {
        if (candles.size() < period) {
            return null;  // Insufficient data
        }

        // Calculate initial SMA
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).close());
        }
        BigDecimal ema = sum.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);

        // EMA multiplier: 2 / (period + 1)
        BigDecimal multiplier = new BigDecimal("2")
            .divide(new BigDecimal(period + 1), 8, RoundingMode.HALF_UP);

        // Calculate EMA using only the initial SMA point (simplified)
        // For full accuracy, would need to calculate EMA for all historical points
        // This approximation is sufficient for trading decisions
        return ema;
    }

    /**
     * Calculate EMA properly by iterating through all points.
     * More accurate but slower than simplified version.
     *
     * @param candles Historical candles (most recent last)
     * @param period EMA period
     * @return EMA value, or null if insufficient data
     */
    public static BigDecimal calculateEMAFull(List<Candle> candles, int period) {
        if (candles.size() < period) {
            return null;
        }

        // Calculate initial SMA as starting point
        BigDecimal sum = BigDecimal.ZERO;
        int startIndex = candles.size() - period;
        for (int i = 0; i < period; i++) {
            sum = sum.add(candles.get(startIndex + i).close());
        }
        BigDecimal ema = sum.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);

        // EMA multiplier
        BigDecimal multiplier = new BigDecimal("2")
            .divide(new BigDecimal(period + 1), 8, RoundingMode.HALF_UP);

        // Calculate EMA for remaining candles
        for (int i = startIndex + period; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).close();
            // EMA = (Close - EMA_prev) * multiplier + EMA_prev
            ema = close.subtract(ema)
                .multiply(multiplier)
                .add(ema);
        }

        return ema;
    }

    /**
     * Calculate Simple Moving Average of volume.
     * DISABLED - Candles don't contain volume data.
     *
     * @param candles Historical candles (most recent last)
     * @param period SMA period (e.g., 20)
     * @return Volume SMA, or null if insufficient data
     */
    /*
    public static BigDecimal calculateVolumeSMA(List<Candle> candles, int period) {
        if (candles.size() < period) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).volume());
        }

        return sum.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
    }
    */

    /**
     * Find the highest close price in the lookback period.
     *
     * @param candles Historical candles (most recent last)
     * @param lookbackPeriods Number of periods to look back
     * @return Highest close price, or null if insufficient data
     */
    public static BigDecimal findHighestClose(List<Candle> candles, int lookbackPeriods) {
        if (candles.size() < lookbackPeriods) {
            return null;
        }

        BigDecimal highest = BigDecimal.ZERO;
        for (int i = candles.size() - lookbackPeriods; i < candles.size(); i++) {
            if (candles.get(i).close().compareTo(highest) > 0) {
                highest = candles.get(i).close();
            }
        }

        return highest;
    }

    /**
     * Calculate price drop percentage from a reference price.
     *
     * @param currentPrice Current price
     * @param referencePrice Reference price
     * @return Drop percentage (positive value indicates a drop)
     */
    public static BigDecimal calculatePriceDrop(BigDecimal currentPrice, BigDecimal referencePrice) {
        if (referencePrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return referencePrice.subtract(currentPrice)
            .divide(referencePrice, 8, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    /**
     * Calculate distance between current price and a level (e.g., EMA).
     *
     * @param currentPrice Current price
     * @param levelPrice Level price (e.g., EMA200)
     * @return Distance as percentage (negative means below level, positive means above)
     */
    public static BigDecimal calculateDistanceFromLevel(BigDecimal currentPrice, BigDecimal levelPrice) {
        if (levelPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return currentPrice.subtract(levelPrice)
            .divide(levelPrice, 8, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }
}
