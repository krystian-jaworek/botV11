package com.tradingbot.core.indicators;

/**
 * Supported timeframes for candle aggregation.
 */
public enum Timeframe {
    M1(1, "1m"),
    M5(5, "5m"),
    M15(15, "15m"),
    M30(30, "30m"),
    H1(60, "1h"),
    H4(240, "4h"),
    D1(1440, "1d"),
    W1(10080, "1w");

    private final int minutes;
    private final String label;

    Timeframe(int minutes, String label) {
        this.minutes = minutes;
        this.label = label;
    }

    public int getMinutes() {
        return minutes;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Get ratio between two timeframes.
     * Example: H1 to D1 = 24 (24 hourly candles = 1 daily candle)
     */
    public int getRatioTo(Timeframe higher) {
        if (higher.minutes < this.minutes) {
            throw new IllegalArgumentException(
                "Target timeframe must be higher than source: " + this + " -> " + higher
            );
        }
        return higher.minutes / this.minutes;
    }

    /**
     * Parse from string label (e.g., "1h", "1d", "1w")
     */
    public static Timeframe fromLabel(String label) {
        for (Timeframe tf : values()) {
            if (tf.label.equalsIgnoreCase(label)) {
                return tf;
            }
        }
        throw new IllegalArgumentException("Unknown timeframe: " + label);
    }

    @Override
    public String toString() {
        return label;
    }
}
