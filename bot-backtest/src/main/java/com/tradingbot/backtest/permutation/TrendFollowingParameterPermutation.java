package com.tradingbot.backtest.permutation;

import com.tradingbot.algorithms.trendfollowing.TrendFollowingConfig;
import com.tradingbot.core.indicators.Timeframe;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates parameter permutations for TrendFollowing algorithm.
 *
 * REDUCED ranges to ~1000 combinations for faster testing.
 */
@Slf4j
@Value
@Builder
public class TrendFollowingParameterPermutation {

    // Timeframes (fixed for simplicity)
    Timeframe primaryTimeframe;
    Timeframe secondaryTimeframe;

    // EMA ranges
    List<Integer> emaFastPrimaryRange;
    List<Integer> emaSlowPrimaryRange;
    List<Integer> emaFastSecondaryRange;
    List<Integer> emaSlowSecondaryRange;

    // MACD ranges
    List<Integer> macdFastRange;
    List<Integer> macdSlowRange;
    List<Integer> macdSignalRange;

    // Higher lows
    List<Integer> higherLogsPeriodsRange;
    List<Integer> swingDetectionPeriodsRange;

    // Histogram
    List<Integer> minHistogramGrowthRange;

    // Exit strategies
    List<List<BigDecimal>> targetProfitPctOptions;
    List<BigDecimal> stopLossPctRange;
    List<BigDecimal> trailingStopActivationPctRange;
    List<BigDecimal> trailingStopDistancePctRange;

    // Position sizing (fixed)
    BigDecimal positionSizePct;
    boolean useAvailableEquity;

    /**
     * Generate all configuration combinations.
     */
    public List<TrendFollowingConfig> generateConfigurations() {
        List<TrendFollowingConfig> configurations = new ArrayList<>();

        for (Integer emaFastPrimary : emaFastPrimaryRange) {
            for (Integer emaSlowPrimary : emaSlowPrimaryRange) {
                // Skip invalid combinations
                if (emaFastPrimary >= emaSlowPrimary) continue;

                for (Integer emaFastSecondary : emaFastSecondaryRange) {
                    for (Integer emaSlowSecondary : emaSlowSecondaryRange) {
                        if (emaFastSecondary >= emaSlowSecondary) continue;

                        for (Integer macdFast : macdFastRange) {
                            for (Integer macdSlow : macdSlowRange) {
                                if (macdFast >= macdSlow) continue;

                                for (Integer macdSignal : macdSignalRange) {
                                    for (Integer higherLowsPeriods : higherLogsPeriodsRange) {
                                        for (Integer swingPeriods : swingDetectionPeriodsRange) {
                                            for (Integer histogramGrowth : minHistogramGrowthRange) {
                                                for (List<BigDecimal> tpLevels : targetProfitPctOptions) {
                                                    for (BigDecimal stopLoss : stopLossPctRange) {
                                                        for (BigDecimal trailingActivation : trailingStopActivationPctRange) {
                                                            for (BigDecimal trailingDistance : trailingStopDistancePctRange) {
                                                                configurations.add(createConfig(
                                                                    emaFastPrimary, emaSlowPrimary,
                                                                    emaFastSecondary, emaSlowSecondary,
                                                                    macdFast, macdSlow, macdSignal,
                                                                    higherLowsPeriods, swingPeriods,
                                                                    histogramGrowth, tpLevels,
                                                                    stopLoss, trailingActivation, trailingDistance
                                                                ));
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        log.info("Generated {} TrendFollowing configurations", configurations.size());
        return configurations;
    }

    private TrendFollowingConfig createConfig(
        int emaFastPrimary, int emaSlowPrimary,
        int emaFastSecondary, int emaSlowSecondary,
        int macdFast, int macdSlow, int macdSignal,
        int higherLowsPeriods, int swingPeriods,
        int histogramGrowth, List<BigDecimal> tpLevels,
        BigDecimal stopLoss, BigDecimal trailingActivation, BigDecimal trailingDistance
    ) {
        return TrendFollowingConfig.builder()
            .primaryTimeframe(primaryTimeframe)
            .secondaryTimeframe(secondaryTimeframe)
            .emaFastPrimary(emaFastPrimary)
            .emaSlowPrimary(emaSlowPrimary)
            .emaFastSecondary(emaFastSecondary)
            .emaSlowSecondary(emaSlowSecondary)
            .macdFast(macdFast)
            .macdSlow(macdSlow)
            .macdSignal(macdSignal)
            .higherLowsPeriods(higherLowsPeriods)
            .swingDetectionPeriods(swingPeriods)
            .higherLowsLookback(20)  // Fixed
            .minHistogramGrowth(histogramGrowth)
            .targetProfitPct(tpLevels)
            .stopLossPct(stopLoss)
            .trailingStopActivationPct(trailingActivation)
            .trailingStopDistancePct(trailingDistance)
            .enableDeathCrossExit(true)  // Fixed
            .positionSizePct(positionSizePct)
            .useAvailableEquity(useAvailableEquity)
            .build();
    }

    /**
     * Create default permutation with REDUCED ranges (~800 combinations).
     */
    public static TrendFollowingParameterPermutation defaultPermutation() {
        return TrendFollowingParameterPermutation.builder()
            // Timeframes (fixed)
            .primaryTimeframe(Timeframe.D1)
            .secondaryTimeframe(Timeframe.W1)

            // EMA PRIMARY (2 × 2 = 4 valid combinations)
            .emaFastPrimaryRange(List.of(50, 100))
            .emaSlowPrimaryRange(List.of(150, 200))  // REDUCED from 3 to 2

            // EMA SECONDARY (2 × 2 = 4 valid combinations)
            .emaFastSecondaryRange(List.of(20, 30))
            .emaSlowSecondaryRange(List.of(50, 100))

            // MACD (1 × 1 × 1 = 1 combination - use defaults only)
            .macdFastRange(List.of(12))      // REDUCED to default only
            .macdSlowRange(List.of(26))      // REDUCED to default only
            .macdSignalRange(List.of(9))     // REDUCED to default only

            // HIGHER LOWS (2 × 2 = 4 combinations)
            .higherLogsPeriodsRange(List.of(3, 4))  // REDUCED from 3 to 2
            .swingDetectionPeriodsRange(List.of(5, 7))

            // HISTOGRAM (2 values)
            .minHistogramGrowthRange(List.of(2, 3))

            // TP LEVELS (2 options)
            .targetProfitPctOptions(List.of(
                List.of(new BigDecimal("200"), new BigDecimal("300"), new BigDecimal("500")),  // Conservative
                List.of(new BigDecimal("250"), new BigDecimal("400"), new BigDecimal("700"))   // Aggressive
            ))

            // STOP LOSS (3 values)
            .stopLossPctRange(List.of(
                new BigDecimal("15"),
                new BigDecimal("20"),
                new BigDecimal("25")
            ))

            // TRAILING STOP (1 × 1 = 1 combination - use defaults only)
            .trailingStopActivationPctRange(List.of(
                new BigDecimal("50")  // REDUCED to default only
            ))
            .trailingStopDistancePctRange(List.of(
                new BigDecimal("20")  // REDUCED to default only
            ))

            // Position sizing (fixed)
            .positionSizePct(new BigDecimal("10"))
            .useAvailableEquity(true)
            .build();
    }

    /**
     * Calculate total permutation count without generating all configs.
     */
    public int calculatePermutationCount() {
        int count = 0;

        for (Integer emaFastPrimary : emaFastPrimaryRange) {
            for (Integer emaSlowPrimary : emaSlowPrimaryRange) {
                if (emaFastPrimary >= emaSlowPrimary) continue;

                for (Integer emaFastSecondary : emaFastSecondaryRange) {
                    for (Integer emaSlowSecondary : emaSlowSecondaryRange) {
                        if (emaFastSecondary >= emaSlowSecondary) continue;

                        for (Integer macdFast : macdFastRange) {
                            for (Integer macdSlow : macdSlowRange) {
                                if (macdFast >= macdSlow) continue;

                                count += macdSignalRange.size()
                                    * higherLogsPeriodsRange.size()
                                    * swingDetectionPeriodsRange.size()
                                    * minHistogramGrowthRange.size()
                                    * targetProfitPctOptions.size()
                                    * stopLossPctRange.size()
                                    * trailingStopActivationPctRange.size()
                                    * trailingStopDistancePctRange.size();
                            }
                        }
                    }
                }
            }
        }

        return count;
    }

    // Getter methods for range information
    public String getEmaFastPrimaryRangeStr() {
        return emaFastPrimaryRange.toString();
    }

    public String getEmaSlowPrimaryRangeStr() {
        return emaSlowPrimaryRange.toString();
    }

    public String getStopLossPctRangeStr() {
        return stopLossPctRange.toString();
    }
}
