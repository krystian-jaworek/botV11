package com.tradingbot.backtest.permutation;

import com.tradingbot.algorithms.opportunisticdca.OpportunisticDCAConfig;
import com.tradingbot.core.models.TradingPair;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parameter permutation generator for Opportunistic DCA algorithm.
 *
 * Permutes:
 * - RSI thresholds for buy tiers
 * - Position sizes for buy tiers
 * - Profit thresholds for sell levels
 * - Close percentages for sell levels
 * - Cooldown hours
 */
public class OpportunisticDCAParameterPermutation {

    // Buy tier 1: RSI thresholds to test
    private final List<Integer> tier1RsiValues;
    // Buy tier 1: Position sizes to test
    private final List<BigDecimal> tier1SizeValues;

    // Buy tier 2: RSI thresholds to test
    private final List<Integer> tier2RsiValues;
    // Buy tier 2: Position sizes to test
    private final List<BigDecimal> tier2SizeValues;

    // Buy tier 3: RSI thresholds to test
    private final List<Integer> tier3RsiValues;
    // Buy tier 3: Position sizes to test
    private final List<BigDecimal> tier3SizeValues;

    // Sell level 1: Profit thresholds to test
    private final List<BigDecimal> sell1ProfitValues;
    // Sell level 1: Close percentages to test
    private final List<BigDecimal> sell1CloseValues;

    // Sell level 2: Profit thresholds to test
    private final List<BigDecimal> sell2ProfitValues;
    // Sell level 2: Close percentages to test
    private final List<BigDecimal> sell2CloseValues;

    // Sell level 3: Profit thresholds to test
    private final List<BigDecimal> sell3ProfitValues;

    // Cooldown hours to test
    private final List<Integer> cooldownHoursValues;

    public OpportunisticDCAParameterPermutation(
        List<Integer> tier1RsiValues,
        List<BigDecimal> tier1SizeValues,
        List<Integer> tier2RsiValues,
        List<BigDecimal> tier2SizeValues,
        List<Integer> tier3RsiValues,
        List<BigDecimal> tier3SizeValues,
        List<BigDecimal> sell1ProfitValues,
        List<BigDecimal> sell1CloseValues,
        List<BigDecimal> sell2ProfitValues,
        List<BigDecimal> sell2CloseValues,
        List<BigDecimal> sell3ProfitValues,
        List<Integer> cooldownHoursValues
    ) {
        this.tier1RsiValues = tier1RsiValues;
        this.tier1SizeValues = tier1SizeValues;
        this.tier2RsiValues = tier2RsiValues;
        this.tier2SizeValues = tier2SizeValues;
        this.tier3RsiValues = tier3RsiValues;
        this.tier3SizeValues = tier3SizeValues;
        this.sell1ProfitValues = sell1ProfitValues;
        this.sell1CloseValues = sell1CloseValues;
        this.sell2ProfitValues = sell2ProfitValues;
        this.sell2CloseValues = sell2CloseValues;
        this.sell3ProfitValues = sell3ProfitValues;
        this.cooldownHoursValues = cooldownHoursValues;
    }

    /**
     * Create default permutation with reasonable ranges
     */
    public static OpportunisticDCAParameterPermutation defaultPermutation() {
        return new OpportunisticDCAParameterPermutation(
            List.of(35, 40, 45),                           // Tier 1 RSI
            List.of(new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("4")),  // Tier 1 size
            List.of(25, 30, 35),                           // Tier 2 RSI
            List.of(new BigDecimal("4"), new BigDecimal("5"), new BigDecimal("6")),  // Tier 2 size
            List.of(15, 20, 25),                           // Tier 3 RSI
            List.of(new BigDecimal("8"), new BigDecimal("10"), new BigDecimal("12")), // Tier 3 size
            List.of(new BigDecimal("8"), new BigDecimal("10"), new BigDecimal("12")), // Sell 1 profit
            List.of(new BigDecimal("40"), new BigDecimal("50"), new BigDecimal("60")), // Sell 1 close %
            List.of(new BigDecimal("12"), new BigDecimal("15"), new BigDecimal("18")), // Sell 2 profit
            List.of(new BigDecimal("40"), new BigDecimal("50"), new BigDecimal("60")), // Sell 2 close %
            List.of(new BigDecimal("25"), new BigDecimal("30"), new BigDecimal("35")), // Sell 3 profit
            List.of(12, 24, 48)                            // Cooldown hours
        );
    }

    /**
     * Create compact permutation for quick testing
     */
    public static OpportunisticDCAParameterPermutation compactPermutation() {
        return new OpportunisticDCAParameterPermutation(
            List.of(40),                      // Tier 1 RSI
            List.of(new BigDecimal("3")),     // Tier 1 size
            List.of(30),                      // Tier 2 RSI
            List.of(new BigDecimal("5")),     // Tier 2 size
            List.of(20),                      // Tier 3 RSI
            List.of(new BigDecimal("10")),    // Tier 3 size
            List.of(new BigDecimal("10")),    // Sell 1 profit
            List.of(new BigDecimal("50")),    // Sell 1 close %
            List.of(new BigDecimal("15")),    // Sell 2 profit
            List.of(new BigDecimal("50")),    // Sell 2 close %
            List.of(new BigDecimal("30")),    // Sell 3 profit
            List.of(24)                       // Cooldown hours
        );
    }

    /**
     * Generate all configuration combinations
     */
    public List<OpportunisticDCAConfig> generateConfigurations() {
        List<OpportunisticDCAConfig> configs = new ArrayList<>();

        for (Integer tier1Rsi : tier1RsiValues) {
            for (BigDecimal tier1Size : tier1SizeValues) {
                for (Integer tier2Rsi : tier2RsiValues) {
                    for (BigDecimal tier2Size : tier2SizeValues) {
                        for (Integer tier3Rsi : tier3RsiValues) {
                            for (BigDecimal tier3Size : tier3SizeValues) {
                                for (BigDecimal sell1Profit : sell1ProfitValues) {
                                    for (BigDecimal sell1Close : sell1CloseValues) {
                                        for (BigDecimal sell2Profit : sell2ProfitValues) {
                                            for (BigDecimal sell2Close : sell2CloseValues) {
                                                for (BigDecimal sell3Profit : sell3ProfitValues) {
                                                    for (Integer cooldown : cooldownHoursValues) {
                                                        OpportunisticDCAConfig config = createConfig(
                                                            tier1Rsi, tier1Size,
                                                            tier2Rsi, tier2Size,
                                                            tier3Rsi, tier3Size,
                                                            sell1Profit, sell1Close,
                                                            sell2Profit, sell2Close,
                                                            sell3Profit,
                                                            cooldown
                                                        );
                                                        configs.add(config);
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

        return configs;
    }

    /**
     * Create a config with specific parameters
     */
    private OpportunisticDCAConfig createConfig(
        int tier1Rsi, BigDecimal tier1Size,
        int tier2Rsi, BigDecimal tier2Size,
        int tier3Rsi, BigDecimal tier3Size,
        BigDecimal sell1Profit, BigDecimal sell1Close,
        BigDecimal sell2Profit, BigDecimal sell2Close,
        BigDecimal sell3Profit,
        int cooldownHours
    ) {
        return OpportunisticDCAConfig.builder()
            .algorithmName("Opportunistic DCA")
            .asset("BTCUSDT")
            .timeframe("1m")
            .tradingPair(TradingPair.BTCUSDT)
            .startingCapital(new BigDecimal("10000"))
            .cooldownHours(cooldownHours)
            .buyTiers(List.of(
                OpportunisticDCAConfig.BuyTier.builder()
                    .rsiThreshold(tier1Rsi)
                    .sizePct(tier1Size)
                    .build(),
                OpportunisticDCAConfig.BuyTier.builder()
                    .rsiThreshold(tier2Rsi)
                    .sizePct(tier2Size)
                    .build(),
                OpportunisticDCAConfig.BuyTier.builder()
                    .rsiThreshold(tier3Rsi)
                    .sizePct(tier3Size)
                    .build()
            ))
            .sellLevels(List.of(
                OpportunisticDCAConfig.SellLevel.builder()
                    .profitPct(sell1Profit)
                    .closePct(sell1Close)
                    .build(),
                OpportunisticDCAConfig.SellLevel.builder()
                    .profitPct(sell2Profit)
                    .closePct(sell2Close)
                    .build(),
                OpportunisticDCAConfig.SellLevel.builder()
                    .profitPct(sell3Profit)
                    .closePct(new BigDecimal("100"))  // Always close 100% at final level
                    .build()
            ))
            .build();
    }
}
