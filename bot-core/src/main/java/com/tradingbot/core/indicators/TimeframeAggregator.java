package com.tradingbot.core.indicators;

import com.tradingbot.core.models.Candle;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates candles from lower timeframe to higher timeframe.
 *
 * Example: 24 x 1h candles → 1 x 1d candle
 */
@Slf4j
public class TimeframeAggregator {

    /**
     * Aggregate candles to a higher timeframe.
     *
     * @param candles Source candles (must be in chronological order)
     * @param sourceTimeframe Source timeframe (e.g., 1h)
     * @param targetTimeframe Target timeframe (e.g., 1d)
     * @return Aggregated candles
     */
    public static List<Candle> aggregate(
        List<Candle> candles,
        Timeframe sourceTimeframe,
        Timeframe targetTimeframe
    ) {
        if (candles == null || candles.isEmpty()) {
            return new ArrayList<>();
        }

        int ratio = sourceTimeframe.getRatioTo(targetTimeframe);

        if (ratio == 1) {
            // No aggregation needed
            return new ArrayList<>(candles);
        }

        List<Candle> aggregated = new ArrayList<>();

        for (int i = 0; i + ratio <= candles.size(); i += ratio) {
            List<Candle> batch = candles.subList(i, i + ratio);
            Candle aggregatedCandle = aggregateBatch(batch);
            aggregated.add(aggregatedCandle);
        }

        log.debug("Aggregated {} candles from {} to {} → {} candles",
            candles.size(), sourceTimeframe, targetTimeframe, aggregated.size());

        return aggregated;
    }

    /**
     * Aggregate a batch of candles into a single candle.
     */
    private static Candle aggregateBatch(List<Candle> batch) {
        if (batch.isEmpty()) {
            throw new IllegalArgumentException("Batch cannot be empty");
        }

        Candle first = batch.get(0);
        Candle last = batch.get(batch.size() - 1);

        BigDecimal open = first.open();
        BigDecimal close = last.close();
        long timestamp = first.timestamp();

        // Find highest high and lowest low
        BigDecimal high = batch.stream()
            .map(Candle::high)
            .max(BigDecimal::compareTo)
            .orElseThrow();

        BigDecimal low = batch.stream()
            .map(Candle::low)
            .min(BigDecimal::compareTo)
            .orElseThrow();

        return new Candle(open, close, high, low, timestamp);
    }

    /**
     * Check if we have enough candles for aggregation.
     */
    public static boolean canAggregate(int candleCount, Timeframe source, Timeframe target) {
        int ratio = source.getRatioTo(target);
        return candleCount >= ratio;
    }

    /**
     * Calculate how many aggregated candles we can produce.
     */
    public static int calculateAggregatedCount(int candleCount, Timeframe source, Timeframe target) {
        int ratio = source.getRatioTo(target);
        return candleCount / ratio;
    }
}
