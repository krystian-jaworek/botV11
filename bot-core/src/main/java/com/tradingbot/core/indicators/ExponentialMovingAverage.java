package com.tradingbot.core.indicators;

import com.tradingbot.core.models.Candle;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Exponential Moving Average (EMA) calculator.
 *
 * EMA gives more weight to recent prices compared to SMA.
 * Formula: EMA = (Close - PreviousEMA) × Multiplier + PreviousEMA
 * Where: Multiplier = 2 / (period + 1)
 */
@Slf4j
public class ExponentialMovingAverage {

    /**
     * Calculate EMA for a list of candles.
     *
     * @param candles List of candles (chronological order)
     * @param period EMA period (e.g., 50, 200)
     * @return List of EMA values (same length as candles, first 'period-1' values are null)
     */
    public static List<BigDecimal> calculate(List<Candle> candles, int period) {
        if (candles == null || candles.isEmpty()) {
            return new ArrayList<>();
        }

        if (period <= 0) {
            throw new IllegalArgumentException("Period must be positive: " + period);
        }

        if (candles.size() < period) {
            log.warn("Not enough candles for EMA calculation: {} < {}", candles.size(), period);
            return createNullList(candles.size());
        }

        List<BigDecimal> emaValues = new ArrayList<>(candles.size());
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));

        // Initialize with nulls for first (period - 1) values
        for (int i = 0; i < period - 1; i++) {
            emaValues.add(null);
        }

        // First EMA = SMA of first 'period' candles
        BigDecimal firstEma = calculateSMA(candles, 0, period);
        emaValues.add(firstEma);

        // Calculate subsequent EMAs
        BigDecimal previousEma = firstEma;

        for (int i = period; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).close();
            BigDecimal ema = calculateEMAValue(close, previousEma, multiplier);
            emaValues.add(ema);
            previousEma = ema;
        }

        return emaValues;
    }

    /**
     * Calculate a single EMA value.
     */
    private static BigDecimal calculateEMAValue(
        BigDecimal close,
        BigDecimal previousEma,
        BigDecimal multiplier
    ) {
        // EMA = (Close - PreviousEMA) × Multiplier + PreviousEMA
        return close.subtract(previousEma)
            .multiply(multiplier)
            .add(previousEma)
            .setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * Calculate Simple Moving Average for initial EMA value.
     */
    private static BigDecimal calculateSMA(List<Candle> candles, int start, int period) {
        BigDecimal sum = BigDecimal.ZERO;

        for (int i = start; i < start + period; i++) {
            sum = sum.add(candles.get(i).close());
        }

        return sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
    }

    /**
     * Create a list filled with nulls.
     */
    private static List<BigDecimal> createNullList(int size) {
        List<BigDecimal> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(null);
        }
        return list;
    }

    /**
     * Get current EMA value (last value in the list).
     */
    public static BigDecimal getCurrent(List<BigDecimal> emaValues) {
        if (emaValues == null || emaValues.isEmpty()) {
            return null;
        }
        return emaValues.get(emaValues.size() - 1);
    }

    /**
     * Get EMA value at specific index.
     */
    public static BigDecimal getAt(List<BigDecimal> emaValues, int index) {
        if (emaValues == null || index < 0 || index >= emaValues.size()) {
            return null;
        }
        return emaValues.get(index);
    }
}
