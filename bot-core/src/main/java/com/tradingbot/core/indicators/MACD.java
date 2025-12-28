package com.tradingbot.core.indicators;

import com.tradingbot.core.models.Candle;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * MACD (Moving Average Convergence Divergence) indicator calculator.
 *
 * MACD Line = EMA(fast) - EMA(slow)
 * Signal Line = EMA(MACD Line, signal period)
 * Histogram = MACD Line - Signal Line
 */
@Slf4j
public class MACD {

    /**
     * MACD calculation result.
     */
    public record MACDResult(
        BigDecimal macdLine,
        BigDecimal signalLine,
        BigDecimal histogram
    ) {
        public boolean isValid() {
            return macdLine != null && signalLine != null && histogram != null;
        }
    }

    /**
     * Calculate MACD for a list of candles.
     *
     * @param candles List of candles (chronological order)
     * @param fastPeriod Fast EMA period (typically 12)
     * @param slowPeriod Slow EMA period (typically 26)
     * @param signalPeriod Signal line period (typically 9)
     * @return List of MACD results (same length as candles)
     */
    public static List<MACDResult> calculate(
        List<Candle> candles,
        int fastPeriod,
        int slowPeriod,
        int signalPeriod
    ) {
        if (candles == null || candles.isEmpty()) {
            return new ArrayList<>();
        }

        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException(
                "Fast period must be less than slow period: " + fastPeriod + " >= " + slowPeriod
            );
        }

        int requiredCandles = slowPeriod + signalPeriod;
        if (candles.size() < requiredCandles) {
            log.warn("Not enough candles for MACD: {} < {}", candles.size(), requiredCandles);
            return createNullResults(candles.size());
        }

        // Calculate fast and slow EMAs
        List<BigDecimal> fastEma = ExponentialMovingAverage.calculate(candles, fastPeriod);
        List<BigDecimal> slowEma = ExponentialMovingAverage.calculate(candles, slowPeriod);

        // Calculate MACD line
        List<BigDecimal> macdLine = calculateMACDLine(fastEma, slowEma);

        // Calculate signal line (EMA of MACD line)
        List<BigDecimal> signalLine = calculateSignalLine(macdLine, signalPeriod);

        // Calculate histogram
        List<MACDResult> results = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            BigDecimal macd = macdLine.get(i);
            BigDecimal signal = signalLine.get(i);

            if (macd != null && signal != null) {
                BigDecimal histogram = macd.subtract(signal).setScale(8, RoundingMode.HALF_UP);
                results.add(new MACDResult(macd, signal, histogram));
            } else {
                results.add(new MACDResult(null, null, null));
            }
        }

        return results;
    }

    /**
     * Calculate MACD line (fast EMA - slow EMA).
     */
    private static List<BigDecimal> calculateMACDLine(
        List<BigDecimal> fastEma,
        List<BigDecimal> slowEma
    ) {
        List<BigDecimal> macdLine = new ArrayList<>(fastEma.size());

        for (int i = 0; i < fastEma.size(); i++) {
            BigDecimal fast = fastEma.get(i);
            BigDecimal slow = slowEma.get(i);

            if (fast != null && slow != null) {
                BigDecimal macd = fast.subtract(slow).setScale(8, RoundingMode.HALF_UP);
                macdLine.add(macd);
            } else {
                macdLine.add(null);
            }
        }

        return macdLine;
    }

    /**
     * Calculate signal line (EMA of MACD line).
     */
    private static List<BigDecimal> calculateSignalLine(
        List<BigDecimal> macdLine,
        int signalPeriod
    ) {
        List<BigDecimal> signalLine = new ArrayList<>(macdLine.size());
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (signalPeriod + 1));

        // Find first non-null MACD value
        int firstValidIndex = -1;
        for (int i = 0; i < macdLine.size(); i++) {
            if (macdLine.get(i) != null) {
                firstValidIndex = i;
                break;
            }
        }

        if (firstValidIndex == -1 || firstValidIndex + signalPeriod > macdLine.size()) {
            return createNullList(macdLine.size());
        }

        // Fill with nulls until we have enough data
        for (int i = 0; i < firstValidIndex + signalPeriod - 1; i++) {
            signalLine.add(null);
        }

        // First signal = SMA of first 'signalPeriod' MACD values
        BigDecimal firstSignal = calculateSMA(macdLine, firstValidIndex, signalPeriod);
        signalLine.add(firstSignal);

        // Calculate subsequent signal values
        BigDecimal previousSignal = firstSignal;

        for (int i = firstValidIndex + signalPeriod; i < macdLine.size(); i++) {
            BigDecimal macd = macdLine.get(i);
            if (macd != null) {
                BigDecimal signal = macd.subtract(previousSignal)
                    .multiply(multiplier)
                    .add(previousSignal)
                    .setScale(8, RoundingMode.HALF_UP);
                signalLine.add(signal);
                previousSignal = signal;
            } else {
                signalLine.add(null);
            }
        }

        return signalLine;
    }

    /**
     * Calculate SMA for signal line initialization.
     */
    private static BigDecimal calculateSMA(List<BigDecimal> values, int start, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;

        for (int i = start; i < start + period && i < values.size(); i++) {
            if (values.get(i) != null) {
                sum = sum.add(values.get(i));
                count++;
            }
        }

        if (count == 0) {
            return BigDecimal.ZERO;
        }

        return sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
    }

    /**
     * Create a list filled with null MACD results.
     */
    private static List<MACDResult> createNullResults(int size) {
        List<MACDResult> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new MACDResult(null, null, null));
        }
        return list;
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
     * Get current MACD result (last value in the list).
     */
    public static MACDResult getCurrent(List<MACDResult> macdResults) {
        if (macdResults == null || macdResults.isEmpty()) {
            return new MACDResult(null, null, null);
        }
        return macdResults.get(macdResults.size() - 1);
    }

    /**
     * Get MACD result at specific index.
     */
    public static MACDResult getAt(List<MACDResult> macdResults, int index) {
        if (macdResults == null || index < 0 || index >= macdResults.size()) {
            return new MACDResult(null, null, null);
        }
        return macdResults.get(index);
    }
}
