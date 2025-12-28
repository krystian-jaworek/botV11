package com.tradingbot.core.indicators;

import com.tradingbot.core.models.Candle;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Swing point detector for identifying local highs and lows.
 *
 * A swing low is a candle where the low is lower than the lows of N candles on both sides.
 * A swing high is a candle where the high is higher than the highs of N candles on both sides.
 */
@Slf4j
public class SwingDetector {

    /**
     * Represents a swing point (high or low).
     */
    public record SwingPoint(
        int index,
        BigDecimal price,
        long timestamp,
        SwingType type
    ) {
        public boolean isLow() {
            return type == SwingType.LOW;
        }

        public boolean isHigh() {
            return type == SwingType.HIGH;
        }
    }

    public enum SwingType {
        LOW, HIGH
    }

    /**
     * Find swing lows in a list of candles.
     *
     * @param candles List of candles (chronological order)
     * @param pivotPeriods Number of candles to check on each side (e.g., 3, 5, 7)
     * @return List of swing low points
     */
    public static List<SwingPoint> findSwingLows(List<Candle> candles, int pivotPeriods) {
        if (candles == null || candles.isEmpty()) {
            return new ArrayList<>();
        }

        if (pivotPeriods <= 0) {
            throw new IllegalArgumentException("Pivot periods must be positive: " + pivotPeriods);
        }

        List<SwingPoint> swingLows = new ArrayList<>();

        // We need at least (2 * pivotPeriods + 1) candles to detect a swing
        int minRequired = 2 * pivotPeriods + 1;
        if (candles.size() < minRequired) {
            log.debug("Not enough candles for swing detection: {} < {}", candles.size(), minRequired);
            return swingLows;
        }

        // Check each potential pivot candle
        for (int i = pivotPeriods; i < candles.size() - pivotPeriods; i++) {
            if (isSwingLow(candles, i, pivotPeriods)) {
                SwingPoint swing = new SwingPoint(
                    i,
                    candles.get(i).low(),
                    candles.get(i).timestamp(),
                    SwingType.LOW
                );
                swingLows.add(swing);
            }
        }

        log.debug("Found {} swing lows in {} candles (pivot period: {})",
            swingLows.size(), candles.size(), pivotPeriods);

        return swingLows;
    }

    /**
     * Find swing highs in a list of candles.
     *
     * @param candles List of candles (chronological order)
     * @param pivotPeriods Number of candles to check on each side
     * @return List of swing high points
     */
    public static List<SwingPoint> findSwingHighs(List<Candle> candles, int pivotPeriods) {
        if (candles == null || candles.isEmpty()) {
            return new ArrayList<>();
        }

        if (pivotPeriods <= 0) {
            throw new IllegalArgumentException("Pivot periods must be positive: " + pivotPeriods);
        }

        List<SwingPoint> swingHighs = new ArrayList<>();

        int minRequired = 2 * pivotPeriods + 1;
        if (candles.size() < minRequired) {
            return swingHighs;
        }

        for (int i = pivotPeriods; i < candles.size() - pivotPeriods; i++) {
            if (isSwingHigh(candles, i, pivotPeriods)) {
                SwingPoint swing = new SwingPoint(
                    i,
                    candles.get(i).high(),
                    candles.get(i).timestamp(),
                    SwingType.HIGH
                );
                swingHighs.add(swing);
            }
        }

        return swingHighs;
    }

    /**
     * Check if candle at index is a swing low.
     * A swing low has a lower low than all surrounding candles within pivotPeriods.
     */
    private static boolean isSwingLow(List<Candle> candles, int index, int pivotPeriods) {
        BigDecimal centerLow = candles.get(index).low();

        // Check left side
        for (int i = index - pivotPeriods; i < index; i++) {
            if (candles.get(i).low().compareTo(centerLow) <= 0) {
                return false;
            }
        }

        // Check right side
        for (int i = index + 1; i <= index + pivotPeriods; i++) {
            if (candles.get(i).low().compareTo(centerLow) <= 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if candle at index is a swing high.
     * A swing high has a higher high than all surrounding candles within pivotPeriods.
     */
    private static boolean isSwingHigh(List<Candle> candles, int index, int pivotPeriods) {
        BigDecimal centerHigh = candles.get(index).high();

        // Check left side
        for (int i = index - pivotPeriods; i < index; i++) {
            if (candles.get(i).high().compareTo(centerHigh) >= 0) {
                return false;
            }
        }

        // Check right side
        for (int i = index + 1; i <= index + pivotPeriods; i++) {
            if (candles.get(i).high().compareTo(centerHigh) >= 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get the most recent N swing lows.
     */
    public static List<SwingPoint> getRecentSwingLows(List<SwingPoint> allSwings, int count) {
        if (allSwings == null || allSwings.isEmpty()) {
            return new ArrayList<>();
        }

        int start = Math.max(0, allSwings.size() - count);
        return new ArrayList<>(allSwings.subList(start, allSwings.size()));
    }

    /**
     * Get swing lows within a lookback period (by index).
     */
    public static List<SwingPoint> getSwingLowsInRange(
        List<SwingPoint> allSwings,
        int fromIndex,
        int toIndex
    ) {
        if (allSwings == null || allSwings.isEmpty()) {
            return new ArrayList<>();
        }

        return allSwings.stream()
            .filter(swing -> swing.index() >= fromIndex && swing.index() <= toIndex)
            .toList();
    }
}
