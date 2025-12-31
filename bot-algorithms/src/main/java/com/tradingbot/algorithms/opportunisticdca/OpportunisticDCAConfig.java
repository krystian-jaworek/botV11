package com.tradingbot.algorithms.opportunisticdca;

import com.tradingbot.core.algorithms.AlgorithmConfig;
import com.tradingbot.core.models.TradingPair;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Configuration for Opportunistic DCA algorithm.
 *
 * Simple RSI-based DCA strategy:
 * - Buy on RSI dips (3 tiers)
 * - Sell on profit targets (3 levels)
 * - 24h cooldown between buys (reset on any TP)
 */
@Value
@Builder
public class OpportunisticDCAConfig implements AlgorithmConfig {

    String algorithmName;
    String asset;
    String timeframe;
    TradingPair tradingPair;
    BigDecimal startingCapital;

    // RSI period (number of candles)
    int rsiPeriod;

    // Buy conditions (RSI-based tiers)
    List<BuyTier> buyTiers;

    // Sell conditions (profit-based levels)
    List<SellLevel> sellLevels;

    // Cooldown between buys (hours)
    int cooldownHours;

    /**
     * RSI-based buy tier
     */
    @Value
    @Builder
    public static class BuyTier {
        int rsiThreshold;        // Buy if RSI < threshold
        BigDecimal sizePct;      // % of portfolio to invest
    }

    /**
     * Profit-based sell level
     */
    @Value
    @Builder
    public static class SellLevel {
        BigDecimal profitPct;    // Trigger when profit >= this %
        BigDecimal closePct;     // Close this % of position (100 = close all)
    }

    @Override
    public String getConfigId() {
        return String.format(
            "OpportunisticDCA[RSI=%d,tiers=%d,levels=%d,cooldown=%dh]",
            rsiPeriod,
            buyTiers.size(),
            sellLevels.size(),
            cooldownHours
        );
    }

    /**
     * Validate configuration
     */
    public void validate() {
        if (rsiPeriod < 2) {
            throw new IllegalArgumentException("RSI period must be >= 2");
        }
        if (buyTiers == null || buyTiers.isEmpty()) {
            throw new IllegalArgumentException("Buy tiers cannot be empty");
        }
        if (sellLevels == null || sellLevels.isEmpty()) {
            throw new IllegalArgumentException("Sell levels cannot be empty");
        }
        if (cooldownHours < 0) {
            throw new IllegalArgumentException("Cooldown hours must be >= 0");
        }
    }

    /**
     * Create default configuration for single run
     */
    public static OpportunisticDCAConfig defaultConfig() {
        return OpportunisticDCAConfig.builder()
            .algorithmName("Opportunistic DCA")
            .asset("BTCUSDT")
            .timeframe("1m")
            .tradingPair(TradingPair.BTCUSDT)
            .startingCapital(new BigDecimal("10000"))
            .rsiPeriod(1440)
            .cooldownHours(24)
            .buyTiers(List.of(
                BuyTier.builder()
                    .rsiThreshold(40)
                    .sizePct(new BigDecimal("3"))
                    .build(),
                BuyTier.builder()
                    .rsiThreshold(30)
                    .sizePct(new BigDecimal("5"))
                    .build(),
                BuyTier.builder()
                    .rsiThreshold(20)
                    .sizePct(new BigDecimal("10"))
                    .build()
            ))
            .sellLevels(List.of(
                SellLevel.builder()
                    .profitPct(new BigDecimal("10"))
                    .closePct(new BigDecimal("50"))
                    .build(),
                SellLevel.builder()
                    .profitPct(new BigDecimal("15"))
                    .closePct(new BigDecimal("50"))
                    .build(),
                SellLevel.builder()
                    .profitPct(new BigDecimal("30"))
                    .closePct(new BigDecimal("100"))
                    .build()
            ))
            .build();
    }
}
