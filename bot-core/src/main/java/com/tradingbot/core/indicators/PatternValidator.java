package com.tradingbot.core.indicators;

import com.tradingbot.core.indicators.MACD.MACDResult;
import com.tradingbot.core.indicators.SwingDetector.SwingPoint;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validates various technical analysis patterns.
 */
@Slf4j
public class PatternValidator {

    /**
     * Check if a golden cross occurred at the given index.
     * Golden cross = fast EMA crosses above slow EMA.
     *
     * @param emaFast Fast EMA values
     * @param emaSlow Slow EMA values
     * @param atIndex Index to check
     * @return true if golden cross detected
     */
    public static boolean isGoldenCross(
        List<BigDecimal> emaFast,
        List<BigDecimal> emaSlow,
        int atIndex
    ) {
        if (!isValidIndex(emaFast, atIndex) || !isValidIndex(emaSlow, atIndex)) {
            return false;
        }

        if (atIndex == 0) {
            return false; // Need previous candle for crossover
        }

        BigDecimal currentFast = emaFast.get(atIndex);
        BigDecimal currentSlow = emaSlow.get(atIndex);
        BigDecimal previousFast = emaFast.get(atIndex - 1);
        BigDecimal previousSlow = emaSlow.get(atIndex - 1);

        if (currentFast == null || currentSlow == null || previousFast == null || previousSlow == null) {
            return false;
        }

        // Previous: fast <= slow
        // Current: fast > slow
        boolean wasBelowOrEqual = previousFast.compareTo(previousSlow) <= 0;
        boolean isAbove = currentFast.compareTo(currentSlow) > 0;

        return wasBelowOrEqual && isAbove;
    }

    /**
     * Check if a death cross occurred at the given index.
     * Death cross = fast EMA crosses below slow EMA.
     *
     * @param emaFast Fast EMA values
     * @param emaSlow Slow EMA values
     * @param atIndex Index to check
     * @return true if death cross detected
     */
    public static boolean isDeathCross(
        List<BigDecimal> emaFast,
        List<BigDecimal> emaSlow,
        int atIndex
    ) {
        if (!isValidIndex(emaFast, atIndex) || !isValidIndex(emaSlow, atIndex)) {
            return false;
        }

        if (atIndex == 0) {
            return false;
        }

        BigDecimal currentFast = emaFast.get(atIndex);
        BigDecimal currentSlow = emaSlow.get(atIndex);
        BigDecimal previousFast = emaFast.get(atIndex - 1);
        BigDecimal previousSlow = emaSlow.get(atIndex - 1);

        if (currentFast == null || currentSlow == null || previousFast == null || previousSlow == null) {
            return false;
        }

        // Previous: fast >= slow
        // Current: fast < slow
        boolean wasAboveOrEqual = previousFast.compareTo(previousSlow) >= 0;
        boolean isBelow = currentFast.compareTo(currentSlow) < 0;

        return wasAboveOrEqual && isBelow;
    }

    /**
     * Check if fast EMA is above slow EMA (no crossover, just current state).
     */
    public static boolean isFastAboveSlow(
        List<BigDecimal> emaFast,
        List<BigDecimal> emaSlow,
        int atIndex
    ) {
        if (!isValidIndex(emaFast, atIndex) || !isValidIndex(emaSlow, atIndex)) {
            return false;
        }

        BigDecimal fast = emaFast.get(atIndex);
        BigDecimal slow = emaSlow.get(atIndex);

        if (fast == null || slow == null) {
            return false;
        }

        return fast.compareTo(slow) > 0;
    }

    /**
     * Check if the swing lows show a pattern of higher lows.
     * Each consecutive low should be higher than the previous one.
     *
     * @param swingLows List of swing lows (chronological order)
     * @param requiredCount Minimum number of higher lows required
     * @return true if pattern detected
     */
    public static boolean hasHigherLows(List<SwingPoint> swingLows, int requiredCount) {
        if (swingLows == null || swingLows.size() < requiredCount) {
            return false;
        }

        if (requiredCount < 2) {
            throw new IllegalArgumentException("Required count must be at least 2");
        }

        // Get the most recent 'requiredCount' swings
        int startIndex = swingLows.size() - requiredCount;
        List<SwingPoint> recentSwings = swingLows.subList(startIndex, swingLows.size());

        // Check if each low is higher than the previous
        for (int i = 1; i < recentSwings.size(); i++) {
            BigDecimal currentLow = recentSwings.get(i).price();
            BigDecimal previousLow = recentSwings.get(i - 1).price();

            if (currentLow.compareTo(previousLow) <= 0) {
                return false; // Not higher
            }
        }

        log.debug("Higher lows pattern detected: {} consecutive higher lows", requiredCount);
        return true;
    }

    /**
     * Check if MACD histogram is growing for at least minPeriods.
     * Each histogram value should be greater than the previous one.
     *
     * @param macdResults MACD calculation results
     * @param minPeriods Minimum number of growing periods
     * @param atIndex Current index (we look back from here)
     * @return true if histogram growing
     */
    public static boolean isHistogramGrowing(
        List<MACDResult> macdResults,
        int minPeriods,
        int atIndex
    ) {
        if (!isValidIndex(macdResults, atIndex)) {
            return false;
        }

        if (atIndex < minPeriods) {
            return false; // Not enough history
        }

        // Check last 'minPeriods' histograms
        for (int i = atIndex - minPeriods + 1; i <= atIndex; i++) {
            if (i == atIndex - minPeriods + 1) {
                continue; // First value, nothing to compare
            }

            MACDResult current = macdResults.get(i);
            MACDResult previous = macdResults.get(i - 1);

            if (!current.isValid() || !previous.isValid()) {
                return false;
            }

            // Current histogram must be > previous histogram
            if (current.histogram().compareTo(previous.histogram()) <= 0) {
                return false;
            }
        }

        log.debug("MACD histogram growing for {} periods at index {}", minPeriods, atIndex);
        return true;
    }

    /**
     * Check if histogram is positive (above zero line).
     */
    public static boolean isHistogramPositive(MACDResult macd) {
        if (macd == null || !macd.isValid()) {
            return false;
        }
        return macd.histogram().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if histogram is negative (below zero line).
     */
    public static boolean isHistogramNegative(MACDResult macd) {
        if (macd == null || !macd.isValid()) {
            return false;
        }
        return macd.histogram().compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Helper: Check if index is valid for the list.
     */
    private static <T> boolean isValidIndex(List<T> list, int index) {
        return list != null && index >= 0 && index < list.size();
    }
}
