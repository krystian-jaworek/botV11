package com.tradingbot.core.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable representation of a candlestick.
 * Thread-safe for use in parallel simulations.
 *
 * JSON format: {"open": 88128.40, "close": 88119.80, "high": 88128.50, "low": 88116.50, "timestamp": "1766250900000"}
 */
public record Candle(
    @JsonProperty("open") BigDecimal open,
    @JsonProperty("close") BigDecimal close,
    @JsonProperty("high") BigDecimal high,
    @JsonProperty("low") BigDecimal low,
    @JsonProperty("timestamp") long timestamp
) {
    public Candle {
        if (open == null || close == null || high == null || low == null) {
            throw new IllegalArgumentException("Candle prices cannot be null");
        }
        if (timestamp <= 0) {
            throw new IllegalArgumentException("Candle timestamp must be positive");
        }
    }

    /**
     * Get timestamp as Instant for easier datetime operations
     */
    public Instant getInstant() {
        return Instant.ofEpochMilli(timestamp);
    }

    /**
     * Check if this is a bullish candle (close > open)
     */
    public boolean isBullish() {
        return close.compareTo(open) > 0;
    }

    /**
     * Check if this is a bearish candle (close < open)
     */
    public boolean isBearish() {
        return close.compareTo(open) < 0;
    }

    @Override
    public String toString() {
        return String.format("Candle[O=%.2f C=%.2f H=%.2f L=%.2f @%d]",
            open, close, high, low, timestamp);
    }
}
