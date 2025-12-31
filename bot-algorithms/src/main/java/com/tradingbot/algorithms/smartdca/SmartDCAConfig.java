package com.tradingbot.algorithms.smartdca;

import com.tradingbot.core.algorithms.AlgorithmConfig;
import com.tradingbot.core.models.TradingPair;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Configuration for Smart Opportunistic DCA algorithm.
 *
 * This algorithm accumulates BTC long-term by buying dips based on:
 * - RSI oversold conditions
 * - Price drops from recent highs
 * - Volume spikes
 * - EMA support levels
 *
 * And manages profits through:
 * - Staged exits at profit milestones
 * - Technical exits on overbought conditions
 * - Trailing stops after significant gains
 */
@Value
@Builder
public class SmartDCAConfig implements AlgorithmConfig {

    String algorithmName;
    String asset;
    String timeframe;
    TradingPair tradingPair;
    BigDecimal startingCapital;
    BigDecimal maxPortfolioAllocationPct;

    BuyConditions buyConditions;
    PositionSizing positionSizing;
    ProfitManagement profitManagement;
    RiskManagement riskManagement;
    Reinvestment reinvestment;

    @Value
    @Builder
    public static class BuyConditions {
        RsiCondition rsi;
        PriceDropCondition priceDrop;
        VolumeCondition volume;
        EmaSupportCondition emaSupport;
        CooldownCondition cooldown;

        @Value
        @Builder
        public static class RsiCondition {
            int period;
            int oversoldThreshold;
            int extremeOversold;
        }

        @Value
        @Builder
        public static class PriceDropCondition {
            BigDecimal minFromLastBuyPct;
            BigDecimal minFromLocalHighPct;
            int localHighLookbackPeriods;
        }

        @Value
        @Builder
        public static class VolumeCondition {
            boolean requireConfirmation;
            BigDecimal spikeMultiplier;
            int maPeriods;
        }

        @Value
        @Builder
        public static class EmaSupportCondition {
            boolean enabled;
            int period;
            BigDecimal maxProximityPct;
        }

        @Value
        @Builder
        public static class CooldownCondition {
            int minHoursBetweenBuys;
        }
    }

    @Value
    @Builder
    public static class PositionSizing {
        String mode;  // "progressive"
        List<PositionTier> tiers;

        @Value
        @Builder
        public static class PositionTier {
            String name;
            TierConditions conditions;
            BigDecimal sizePct;

            @Value
            @Builder
            public static class TierConditions {
                List<Integer> rsiRange;      // [min, max]
                List<BigDecimal> priceDropRange;  // [min, max]
            }
        }
    }

    @Value
    @Builder
    public static class ProfitManagement {
        StagedExits stagedExits;
        TechnicalExits technicalExits;
        TrailingStop trailingStop;

        @Value
        @Builder
        public static class StagedExits {
            boolean enabled;
            List<ExitStage> stages;

            @Value
            @Builder
            public static class ExitStage {
                BigDecimal profitThresholdPct;
                BigDecimal closePct;
                Boolean moveStopToBreakeven;
                Boolean activateTrailing;
                Boolean tightenTrailing;
            }
        }

        @Value
        @Builder
        public static class TechnicalExits {
            boolean enabled;
            TechnicalExitRule normal;
            TechnicalExitRule extreme;

            @Value
            @Builder
            public static class TechnicalExitRule {
                int rsiThreshold;
                Boolean requireAboveEma200;
                BigDecimal minProfitPct;
                BigDecimal closePct;
            }
        }

        @Value
        @Builder
        public static class TrailingStop {
            boolean enabled;
            BigDecimal activationProfitPct;
            String appliesTo;  // "remaining_position"
            List<TrailingTier> dynamicTiers;

            @Value
            @Builder
            public static class TrailingTier {
                List<BigDecimal> profitRange;  // [min, max]
                BigDecimal distancePct;
            }
        }
    }

    @Value
    @Builder
    public static class RiskManagement {
        int maxConcurrentPositions;
        BigDecimal maxDrawdownPct;
        DrawdownAction drawdownAction;

        @Value
        @Builder
        public static class DrawdownAction {
            boolean enabled;
            BigDecimal thresholdPct;
            String action;  // "pause_buying"
            int cooldownDays;
            String resumeCondition;  // "price_recovery_5pct"
        }
    }

    @Value
    @Builder
    public static class Reinvestment {
        boolean enabled;
        String mode;  // "full_reinvest"
        boolean addToAvailableCapital;
        boolean waitForNextSignal;
    }

    @Override
    public String getConfigId() {
        return String.format(
            "SmartDCA[rsi_os=%d,drop=%.1f%%,tp_stages=%d,maxDD=%.1f%%]",
            buyConditions.getRsi().getOversoldThreshold(),
            buyConditions.getPriceDrop().getMinFromLastBuyPct(),
            profitManagement.getStagedExits().getStages().size(),
            riskManagement.getMaxDrawdownPct()
        );
    }

    /**
     * Create default configuration for testing
     */
    public static SmartDCAConfig defaultConfig() {
        return SmartDCAConfig.builder()
            .algorithmName("Smart Opportunistic DCA")
            .asset("BTCUSDT")
            .timeframe("1m")  // 1-minute candles
            .tradingPair(TradingPair.BTCUSDT)
            .startingCapital(new BigDecimal("10000"))
            .maxPortfolioAllocationPct(new BigDecimal("100"))
            .buyConditions(createDefaultBuyConditions())
            .positionSizing(createDefaultPositionSizing())
            .profitManagement(createDefaultProfitManagement())
            .riskManagement(createDefaultRiskManagement())
            .reinvestment(createDefaultReinvestment())
            .build();
    }

    private static BuyConditions createDefaultBuyConditions() {
        return BuyConditions.builder()
            .rsi(BuyConditions.RsiCondition.builder()
                .period(14)
                .oversoldThreshold(35)  // More strict - reduced from 45
                .extremeOversold(25)    // More strict - reduced from 35
                .build())
            .priceDrop(BuyConditions.PriceDropCondition.builder()
                .minFromLastBuyPct(new BigDecimal("4"))  // More strict - increased from 2%
                .minFromLocalHighPct(new BigDecimal("8"))  // More strict - increased from 3%
                .localHighLookbackPeriods(480)  // 8 hours for 1-min candles
                .build())
            .volume(BuyConditions.VolumeCondition.builder()
                .requireConfirmation(true)
                .spikeMultiplier(new BigDecimal("1.8"))  // More strict - increased from 1.4
                .maPeriods(20)
                .build())
            .emaSupport(BuyConditions.EmaSupportCondition.builder()
                .enabled(true)
                .period(200)  // ~3.3 hours for 1-min candles
                .maxProximityPct(new BigDecimal("15"))
                .build())
            .cooldown(BuyConditions.CooldownCondition.builder()
                .minHoursBetweenBuys(24)  // Much longer - increased from 4h to 24h
                .build())
            .build();
    }

    private static PositionSizing createDefaultPositionSizing() {
        return PositionSizing.builder()
            .mode("progressive")
            .tiers(List.of(
                PositionSizing.PositionTier.builder()
                    .name("Tier 1 - Small Dip")
                    .conditions(PositionSizing.PositionTier.TierConditions.builder()
                        .rsiRange(List.of(30, 35))  // Stricter - reduced from [40,45]
                        .priceDropRange(List.of(new BigDecimal("5"), new BigDecimal("10")))  // Larger drops - from [1,3]
                        .build())
                    .sizePct(new BigDecimal("3"))  // Smaller size - reduced from 4%
                    .build(),
                PositionSizing.PositionTier.builder()
                    .name("Tier 2 - Medium Dip")
                    .conditions(PositionSizing.PositionTier.TierConditions.builder()
                        .rsiRange(List.of(25, 30))  // Stricter - reduced from [35,40]
                        .priceDropRange(List.of(new BigDecimal("10"), new BigDecimal("18")))  // Larger drops - from [3,6]
                        .build())
                    .sizePct(new BigDecimal("6"))  // Smaller size - reduced from 7%
                    .build(),
                PositionSizing.PositionTier.builder()
                    .name("Tier 3 - Deep Dip (Bargain)")
                    .conditions(PositionSizing.PositionTier.TierConditions.builder()
                        .rsiRange(List.of(0, 25))  // Stricter - reduced from [0,35]
                        .priceDropRange(List.of(new BigDecimal("18"), new BigDecimal("999")))  // Larger drops - from [6,999]
                        .build())
                    .sizePct(new BigDecimal("10"))  // Smaller size - reduced from 12%
                    .build()
            ))
            .build();
    }

    private static ProfitManagement createDefaultProfitManagement() {
        return ProfitManagement.builder()
            .stagedExits(ProfitManagement.StagedExits.builder()
                .enabled(true)
                .stages(List.of(
                    ProfitManagement.StagedExits.ExitStage.builder()
                        .profitThresholdPct(new BigDecimal("20"))
                        .closePct(new BigDecimal("15"))
                        .moveStopToBreakeven(true)
                        .build(),
                    ProfitManagement.StagedExits.ExitStage.builder()
                        .profitThresholdPct(new BigDecimal("40"))
                        .closePct(new BigDecimal("25"))
                        .activateTrailing(true)
                        .build(),
                    ProfitManagement.StagedExits.ExitStage.builder()
                        .profitThresholdPct(new BigDecimal("80"))
                        .closePct(new BigDecimal("40"))
                        .tightenTrailing(true)
                        .build()
                ))
                .build())
            .technicalExits(ProfitManagement.TechnicalExits.builder()
                .enabled(true)
                .normal(ProfitManagement.TechnicalExits.TechnicalExitRule.builder()
                    .rsiThreshold(80)  // Much stricter - increased from 72
                    .requireAboveEma200(true)
                    .minProfitPct(new BigDecimal("50"))  // Much higher - increased from 18%
                    .closePct(new BigDecimal("20"))
                    .build())
                .extreme(ProfitManagement.TechnicalExits.TechnicalExitRule.builder()
                    .rsiThreshold(90)  // Much stricter - increased from 82
                    .requireAboveEma200(null)
                    .minProfitPct(new BigDecimal("30"))  // Much higher - increased from 12%
                    .closePct(new BigDecimal("35"))
                    .build())
                .build())
            .trailingStop(ProfitManagement.TrailingStop.builder()
                .enabled(true)
                .activationProfitPct(new BigDecimal("80"))  // Much higher - increased from 25%
                .appliesTo("remaining_position")
                .dynamicTiers(List.of(
                    ProfitManagement.TrailingStop.TrailingTier.builder()
                        .profitRange(List.of(new BigDecimal("80"), new BigDecimal("120")))  // Higher range
                        .distancePct(new BigDecimal("30"))  // Wider - increased from 18%
                        .build(),
                    ProfitManagement.TrailingStop.TrailingTier.builder()
                        .profitRange(List.of(new BigDecimal("120"), new BigDecimal("200")))
                        .distancePct(new BigDecimal("25"))
                        .build(),
                    ProfitManagement.TrailingStop.TrailingTier.builder()
                        .profitRange(List.of(new BigDecimal("200"), new BigDecimal("999")))
                        .distancePct(new BigDecimal("20"))
                        .build()
                ))
                .build())
            .build();
    }

    private static RiskManagement createDefaultRiskManagement() {
        return RiskManagement.builder()
            .maxConcurrentPositions(15)
            .maxDrawdownPct(new BigDecimal("20"))
            .drawdownAction(RiskManagement.DrawdownAction.builder()
                .enabled(true)
                .thresholdPct(new BigDecimal("20"))
                .action("pause_buying")
                .cooldownDays(7)
                .resumeCondition("price_recovery_5pct")
                .build())
            .build();
    }

    private static Reinvestment createDefaultReinvestment() {
        return Reinvestment.builder()
            .enabled(true)
            .mode("full_reinvest")
            .addToAvailableCapital(true)
            .waitForNextSignal(true)
            .build();
    }

    /**
     * Validate configuration
     */
    public void validate() {
        if (buyConditions.getRsi().getPeriod() < 2) {
            throw new IllegalArgumentException("RSI period must be at least 2");
        }
        if (buyConditions.getRsi().getOversoldThreshold() <= 0 ||
            buyConditions.getRsi().getOversoldThreshold() > 100) {
            throw new IllegalArgumentException("RSI oversold threshold must be between 1 and 100");
        }
        if (positionSizing.getTiers().isEmpty()) {
            throw new IllegalArgumentException("At least one position sizing tier required");
        }
        if (riskManagement.getMaxDrawdownPct().compareTo(BigDecimal.ZERO) <= 0 ||
            riskManagement.getMaxDrawdownPct().compareTo(new BigDecimal("100")) >= 0) {
            throw new IllegalArgumentException("Max drawdown must be between 0 and 100");
        }
    }
}
