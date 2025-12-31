package com.tradingbot.algorithms.smaopportunistic;

import com.tradingbot.core.algorithms.AlgorithmConfig;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Configuration for SMA Opportunistic algorithm.
 *
 * Simple Moving Average based opportunistic trading strategy.
 */
@Value
@Builder
public class SMAOpportunisticConfig implements AlgorithmConfig {

    /**
     * SMA period (number of candles)
     * Example: 1440 = 24h with 1m candles
     */
    int smaPeriod;

    @Override
    public String getConfigId() {
        return String.format("SMAOpportunistic[SMA=%d]", smaPeriod);
    }

    /**
     * Validate configuration
     */
    public void validate() {
        if (smaPeriod < 2) {
            throw new IllegalArgumentException("SMA period must be >= 2");
        }
    }

    /**
     * Create default configuration for single run
     * - SMA period: 1440 (24h with 1m candles)
     */
    public static SMAOpportunisticConfig defaultConfig() {
        return SMAOpportunisticConfig.builder()
            .smaPeriod(1440)
            .build();
    }
}
