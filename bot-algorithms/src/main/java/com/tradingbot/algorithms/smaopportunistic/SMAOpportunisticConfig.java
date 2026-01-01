package com.tradingbot.algorithms.smaopportunistic;

import com.tradingbot.core.algorithms.AlgorithmConfig;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Configuration for SMA Opportunistic algorithm.
 *
 * SMA-based DCA strategy with cooldown period:
 * - Buy when price < SMA - X%
 * - Position size: Y% of portfolio
 * - Take profit: entry price + Z%
 * - Cooldown: B hours between buys
 */
@Value
@Builder
public class SMAOpportunisticConfig implements AlgorithmConfig {

    /**
     * SMA period (number of candles)
     * Example: 1440 = 24h with 1m candles
     */
    int smaPeriod;

    /**
     * Y: Position size as % of portfolio
     * Example: 5.0 = 5% of portfolio per trade
     */
    BigDecimal positionSizePercent;

    /**
     * X: SMA deviation % threshold for entry
     * Example: 2.0 = buy when price < SMA - 2%
     */
    BigDecimal smaDeviationPercent;

    /**
     * Z: Take profit %
     * Example: 3.0 = close position at entry + 3%
     */
    BigDecimal takeProfitPercent;

    /**
     * B: Cooldown period in hours
     * Example: 24 = 24 hours between buys
     */
    int cooldownHours;

    /**
     * Minimum price drop % from last buy to allow new purchase
     * Example: 1.0 = only buy if price is 1% lower than last buy price
     * This resets after each TP (take profit)
     */
    BigDecimal minPriceDropPercent;

    /**
     * Aggressive DCA trigger: price drop % from last buy that bypasses cooldown
     * Example: 5.0 = if price drops 5% from last buy, immediately DCA and reset cooldown
     */
    BigDecimal aggressiveDcaDropPercent;

    @Override
    public String getConfigId() {
        return String.format("SMAOpp[SMA=%d,Size=%.1f%%,Dev=%.1f%%,TP=%.1f%%,Cool=%dh,Drop=%.1f%%,Aggr=%.1f%%]",
            smaPeriod,
            positionSizePercent,
            smaDeviationPercent,
            takeProfitPercent,
            cooldownHours,
            minPriceDropPercent,
            aggressiveDcaDropPercent
        );
    }

    /**
     * Validate configuration
     */
    public void validate() {
        if (smaPeriod < 2) {
            throw new IllegalArgumentException("SMA period must be >= 2");
        }
        if (positionSizePercent.compareTo(BigDecimal.ZERO) <= 0 || positionSizePercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Position size must be between 0 and 100%");
        }
        if (smaDeviationPercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("SMA deviation must be > 0");
        }
        if (takeProfitPercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Take profit must be > 0");
        }
        if (cooldownHours < 0) {
            throw new IllegalArgumentException("Cooldown hours must be >= 0");
        }
        if (minPriceDropPercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Min price drop percent must be >= 0");
        }
        if (aggressiveDcaDropPercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Aggressive DCA drop percent must be >= 0");
        }
    }

    /**
     * Create default configuration for single run
     *
     * Parameters:
     * - SMA period: 1440 (24h with 1m candles)
     * - Y: Position size: 5% of portfolio
     * - X: SMA deviation: 2% (buy when price < SMA - 2%)
     * - Z: Take profit: 3%
     * - B: Cooldown: 24 hours
     * - Min price drop: 1% (from last buy)
     * - Aggressive DCA: 5% (bypass cooldown if price drops 5%)
     */
    public static SMAOpportunisticConfig defaultConfig() {
        return SMAOpportunisticConfig.builder()
            .smaPeriod(840)
            .positionSizePercent(new BigDecimal("7.0"))
            .smaDeviationPercent(new BigDecimal("2.5"))
            .takeProfitPercent(new BigDecimal("3.0"))
            .cooldownHours(12)
            .minPriceDropPercent(new BigDecimal("1.0"))
            .aggressiveDcaDropPercent(new BigDecimal("3.0"))
            .build();
    }
}
