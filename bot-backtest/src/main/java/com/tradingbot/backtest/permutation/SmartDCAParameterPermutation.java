package com.tradingbot.backtest.permutation;

import com.tradingbot.algorithms.smartdca.SmartDCAConfig;
import com.tradingbot.core.models.TradingPair;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parameter permutation generator for Smart DCA algorithm.
 *
 * For initial testing, we'll permute key parameters:
 * - RSI oversold threshold
 * - Minimum price drop from last buy
 * - Volume spike multiplier
 * - Position sizing percentages
 */
public class SmartDCAParameterPermutation {

    // RSI oversold thresholds to test
    private final List<Integer> rsiOversoldValues;

    // Min price drop from last buy (%)
    private final List<BigDecimal> minPriceDropValues;

    // Volume spike multipliers
    private final List<BigDecimal> volumeSpikeValues;

    // Tier 1 position size (%)
    private final List<BigDecimal> tier1SizeValues;

    public SmartDCAParameterPermutation(
        List<Integer> rsiOversoldValues,
        List<BigDecimal> minPriceDropValues,
        List<BigDecimal> volumeSpikeValues,
        List<BigDecimal> tier1SizeValues
    ) {
        this.rsiOversoldValues = rsiOversoldValues;
        this.minPriceDropValues = minPriceDropValues;
        this.volumeSpikeValues = volumeSpikeValues;
        this.tier1SizeValues = tier1SizeValues;
    }

    /**
     * Create default permutation with reasonable ranges for 1-minute candles
     */
    public static SmartDCAParameterPermutation defaultPermutation() {
        return new SmartDCAParameterPermutation(
            List.of(40, 45, 50),  // RSI thresholds for 1-min candles
            List.of(new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3")),  // Min price drop for 1-min
            List.of(new BigDecimal("1.2"), new BigDecimal("1.4"), new BigDecimal("1.6")),  // Volume spike (unused)
            List.of(new BigDecimal("3"), new BigDecimal("4"), new BigDecimal("5"))  // Tier 1 size
        );
    }

    /**
     * Create compact permutation for quick testing (fewer combinations)
     */
    public static SmartDCAParameterPermutation compactPermutation() {
        return new SmartDCAParameterPermutation(
            List.of(45),  // Single RSI for 1-min candles
            List.of(new BigDecimal("2")),  // Single price drop for 1-min
            List.of(new BigDecimal("1.4")),  // Single volume spike (unused)
            List.of(new BigDecimal("4"))  // Single tier size
        );
    }

    /**
     * Generate all configuration combinations
     */
    public List<SmartDCAConfig> generateConfigurations() {
        List<SmartDCAConfig> configs = new ArrayList<>();

        for (Integer rsiOversold : rsiOversoldValues) {
            for (BigDecimal minPriceDrop : minPriceDropValues) {
                for (BigDecimal volumeSpike : volumeSpikeValues) {
                    for (BigDecimal tier1Size : tier1SizeValues) {
                        SmartDCAConfig config = createConfig(
                            rsiOversold,
                            minPriceDrop,
                            volumeSpike,
                            tier1Size
                        );
                        configs.add(config);
                    }
                }
            }
        }

        return configs;
    }

    /**
     * Create a config with specific parameters
     */
    private SmartDCAConfig createConfig(
        int rsiOversold,
        BigDecimal minPriceDrop,
        BigDecimal volumeSpike,
        BigDecimal tier1Size
    ) {
        // Scale other tiers proportionally
        BigDecimal tier2Size = tier1Size.multiply(new BigDecimal("1.75"));  // ~7% if tier1=4%
        BigDecimal tier3Size = tier1Size.multiply(new BigDecimal("3.0"));   // ~12% if tier1=4%

        return SmartDCAConfig.builder()
            .algorithmName("Smart Opportunistic DCA")
            .asset("BTCUSDT")
            .timeframe("4h")
            .tradingPair(TradingPair.BTCUSDT)
            .startingCapital(new BigDecimal("10000"))
            .maxPortfolioAllocationPct(new BigDecimal("100"))
            .buyConditions(SmartDCAConfig.BuyConditions.builder()
                .rsi(SmartDCAConfig.BuyConditions.RsiCondition.builder()
                    .period(14)
                    .oversoldThreshold(rsiOversold)
                    .extremeOversold(Math.max(rsiOversold - 10, 25))
                    .build())
                .priceDrop(SmartDCAConfig.BuyConditions.PriceDropCondition.builder()
                    .minFromLastBuyPct(minPriceDrop)
                    .minFromLocalHighPct(new BigDecimal("6"))
                    .localHighLookbackPeriods(20)
                    .build())
                .volume(SmartDCAConfig.BuyConditions.VolumeCondition.builder()
                    .requireConfirmation(true)
                    .spikeMultiplier(volumeSpike)
                    .maPeriods(20)
                    .build())
                .emaSupport(SmartDCAConfig.BuyConditions.EmaSupportCondition.builder()
                    .enabled(true)
                    .period(200)
                    .maxProximityPct(new BigDecimal("12"))
                    .build())
                .cooldown(SmartDCAConfig.BuyConditions.CooldownCondition.builder()
                    .minHoursBetweenBuys(24)
                    .build())
                .build())
            .positionSizing(SmartDCAConfig.PositionSizing.builder()
                .mode("progressive")
                .tiers(List.of(
                    SmartDCAConfig.PositionSizing.PositionTier.builder()
                        .name("Tier 1 - Small Dip")
                        .conditions(SmartDCAConfig.PositionSizing.PositionTier.TierConditions.builder()
                            .rsiRange(List.of(rsiOversold - 5, rsiOversold))
                            .priceDropRange(List.of(new BigDecimal("4"), new BigDecimal("8")))
                            .build())
                        .sizePct(tier1Size)
                        .build(),
                    SmartDCAConfig.PositionSizing.PositionTier.builder()
                        .name("Tier 2 - Medium Dip")
                        .conditions(SmartDCAConfig.PositionSizing.PositionTier.TierConditions.builder()
                            .rsiRange(List.of(Math.max(rsiOversold - 12, 25), rsiOversold - 5))
                            .priceDropRange(List.of(new BigDecimal("8"), new BigDecimal("15")))
                            .build())
                        .sizePct(tier2Size)
                        .build(),
                    SmartDCAConfig.PositionSizing.PositionTier.builder()
                        .name("Tier 3 - Deep Dip (Bargain)")
                        .conditions(SmartDCAConfig.PositionSizing.PositionTier.TierConditions.builder()
                            .rsiRange(List.of(0, Math.max(rsiOversold - 12, 25)))
                            .priceDropRange(List.of(new BigDecimal("15"), new BigDecimal("999")))
                            .build())
                        .sizePct(tier3Size)
                        .build()
                ))
                .build())
            .profitManagement(createDefaultProfitManagement())
            .riskManagement(createDefaultRiskManagement())
            .reinvestment(createDefaultReinvestment())
            .build();
    }

    private SmartDCAConfig.ProfitManagement createDefaultProfitManagement() {
        return SmartDCAConfig.ProfitManagement.builder()
            .stagedExits(SmartDCAConfig.ProfitManagement.StagedExits.builder()
                .enabled(true)
                .stages(List.of(
                    SmartDCAConfig.ProfitManagement.StagedExits.ExitStage.builder()
                        .profitThresholdPct(new BigDecimal("20"))
                        .closePct(new BigDecimal("15"))
                        .moveStopToBreakeven(true)
                        .build(),
                    SmartDCAConfig.ProfitManagement.StagedExits.ExitStage.builder()
                        .profitThresholdPct(new BigDecimal("40"))
                        .closePct(new BigDecimal("25"))
                        .activateTrailing(true)
                        .build(),
                    SmartDCAConfig.ProfitManagement.StagedExits.ExitStage.builder()
                        .profitThresholdPct(new BigDecimal("80"))
                        .closePct(new BigDecimal("40"))
                        .tightenTrailing(true)
                        .build()
                ))
                .build())
            .technicalExits(SmartDCAConfig.ProfitManagement.TechnicalExits.builder()
                .enabled(true)
                .normal(SmartDCAConfig.ProfitManagement.TechnicalExits.TechnicalExitRule.builder()
                    .rsiThreshold(72)
                    .requireAboveEma200(true)
                    .minProfitPct(new BigDecimal("18"))
                    .closePct(new BigDecimal("20"))
                    .build())
                .extreme(SmartDCAConfig.ProfitManagement.TechnicalExits.TechnicalExitRule.builder()
                    .rsiThreshold(82)
                    .requireAboveEma200(null)
                    .minProfitPct(new BigDecimal("12"))
                    .closePct(new BigDecimal("35"))
                    .build())
                .build())
            .trailingStop(SmartDCAConfig.ProfitManagement.TrailingStop.builder()
                .enabled(true)
                .activationProfitPct(new BigDecimal("25"))
                .appliesTo("remaining_position")
                .dynamicTiers(List.of(
                    SmartDCAConfig.ProfitManagement.TrailingStop.TrailingTier.builder()
                        .profitRange(List.of(new BigDecimal("25"), new BigDecimal("45")))
                        .distancePct(new BigDecimal("18"))
                        .build(),
                    SmartDCAConfig.ProfitManagement.TrailingStop.TrailingTier.builder()
                        .profitRange(List.of(new BigDecimal("45"), new BigDecimal("75")))
                        .distancePct(new BigDecimal("14"))
                        .build(),
                    SmartDCAConfig.ProfitManagement.TrailingStop.TrailingTier.builder()
                        .profitRange(List.of(new BigDecimal("75"), new BigDecimal("999")))
                        .distancePct(new BigDecimal("10"))
                        .build()
                ))
                .build())
            .build();
    }

    private SmartDCAConfig.RiskManagement createDefaultRiskManagement() {
        return SmartDCAConfig.RiskManagement.builder()
            .maxConcurrentPositions(15)
            .maxDrawdownPct(new BigDecimal("20"))
            .drawdownAction(SmartDCAConfig.RiskManagement.DrawdownAction.builder()
                .enabled(true)
                .thresholdPct(new BigDecimal("20"))
                .action("pause_buying")
                .cooldownDays(7)
                .resumeCondition("price_recovery_5pct")
                .build())
            .build();
    }

    private SmartDCAConfig.Reinvestment createDefaultReinvestment() {
        return SmartDCAConfig.Reinvestment.builder()
            .enabled(true)
            .mode("full_reinvest")
            .addToAvailableCapital(true)
            .waitForNextSignal(true)
            .build();
    }

    /**
     * Calculate total number of configurations
     */
    public int countConfigurations() {
        return rsiOversoldValues.size() *
               minPriceDropValues.size() *
               volumeSpikeValues.size() *
               tier1SizeValues.size();
    }
}
