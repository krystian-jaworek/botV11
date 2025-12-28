package com.tradingbot.algorithms.trendfollowing;

import com.tradingbot.core.algorithms.AlgorithmConfig;
import com.tradingbot.core.indicators.Timeframe;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Configuration for TrendFollowing algorithm.
 *
 * This algorithm uses multiple timeframes, EMAs, MACD, and swing detection
 * to identify trending markets and generate entry/exit signals.
 */
@Value
@Builder
public class TrendFollowingConfig implements AlgorithmConfig {

    // ========== Timeframes ==========
    Timeframe primaryTimeframe;
    Timeframe secondaryTimeframe;

    // ========== EMA Settings ==========
    int emaFastPrimary;
    int emaSlowPrimary;
    int emaFastSecondary;
    int emaSlowSecondary;

    // ========== MACD Settings ==========
    int macdFast;
    int macdSlow;
    int macdSignal;

    // ========== Higher Lows Detection ==========
    int higherLowsPeriods;      // Required number of higher lows (e.g., 3)
    int swingDetectionPeriods;  // Pivot periods for swing detection (e.g., 5)
    int higherLowsLookback;     // How far back to search for swings

    // ========== Histogram Growth ==========
    int minHistogramGrowth;     // Minimum growing histogram periods

    // ========== Exit Strategies ==========
    List<BigDecimal> targetProfitPct;        // TP levels: [200, 300, 500]
    BigDecimal stopLossPct;                  // Stop loss percentage
    BigDecimal trailingStopActivationPct;    // Profit % to activate trailing
    BigDecimal trailingStopDistancePct;      // Distance from peak
    boolean enableDeathCrossExit;             // Exit on death cross

    // ========== Position Sizing ==========
    BigDecimal positionSizePct;               // % of equity to use
    boolean useAvailableEquity;               // Use available vs total equity

    /**
     * Create a default configuration for testing.
     */
    public static TrendFollowingConfig createDefault() {
        return TrendFollowingConfig.builder()
            // Timeframes
            .primaryTimeframe(Timeframe.D1)
            .secondaryTimeframe(Timeframe.W1)

            // EMA
            .emaFastPrimary(50)
            .emaSlowPrimary(200)
            .emaFastSecondary(20)
            .emaSlowSecondary(50)

            // MACD
            .macdFast(12)
            .macdSlow(26)
            .macdSignal(9)

            // Higher lows
            .higherLowsPeriods(3)
            .swingDetectionPeriods(5)
            .higherLowsLookback(20)

            // Histogram
            .minHistogramGrowth(2)

            // Exit strategies
            .targetProfitPct(List.of(
                new BigDecimal("200"),
                new BigDecimal("300"),
                new BigDecimal("500")
            ))
            .stopLossPct(new BigDecimal("15"))
            .trailingStopActivationPct(new BigDecimal("50"))
            .trailingStopDistancePct(new BigDecimal("20"))
            .enableDeathCrossExit(true)

            // Position sizing
            .positionSizePct(new BigDecimal("10"))
            .useAvailableEquity(true)
            .build();
    }

    @Override
    public String getConfigId() {
        return String.format(
            "TrendFollowing[TF:%s/%s,EMA:%d/%d,MACD:%d/%d/%d,HL:%d,TP:%s,SL:%.0f%%]",
            primaryTimeframe,
            secondaryTimeframe,
            emaFastPrimary,
            emaSlowPrimary,
            macdFast,
            macdSlow,
            macdSignal,
            higherLowsPeriods,
            targetProfitPct.size(),
            stopLossPct
        );
    }

    @Override
    public String toString() {
        return String.format(
            "TrendFollowing[TF:%s/%s, EMA:%d/%d, MACD:%d/%d/%d, HL:%d, TP:%s, SL:%.1f%%, TS:%.1f%%(%.1f%%)]",
            primaryTimeframe,
            secondaryTimeframe,
            emaFastPrimary,
            emaSlowPrimary,
            macdFast,
            macdSlow,
            macdSignal,
            higherLowsPeriods,
            targetProfitPct,
            stopLossPct,
            trailingStopActivationPct,
            trailingStopDistancePct
        );
    }
}
