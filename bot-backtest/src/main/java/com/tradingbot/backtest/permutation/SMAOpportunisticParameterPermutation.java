package com.tradingbot.backtest.permutation;

import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parameter permutation generator for SMA Opportunistic algorithm.
 *
 * Permutes:
 * - SMA period (number of candles)
 * - Y: Position size %
 * - X: SMA deviation %
 * - Z: Take profit %
 * - B: Cooldown hours
 * - Min price drop % (from last buy)
 * - Aggressive DCA drop % (bypass cooldown)
 */
public class SMAOpportunisticParameterPermutation {

    private final List<Integer> smaPeriodValues;
    private final List<BigDecimal> positionSizeValues;      // Y
    private final List<BigDecimal> smaDeviationValues;      // X
    private final List<BigDecimal> takeProfitValues;        // Z
    private final List<Integer> cooldownHoursValues;        // B
    private final List<BigDecimal> minPriceDropValues;      // Min price drop %
    private final List<BigDecimal> aggressiveDcaDropValues; // Aggressive DCA %

    public SMAOpportunisticParameterPermutation(
        List<Integer> smaPeriodValues,
        List<BigDecimal> positionSizeValues,
        List<BigDecimal> smaDeviationValues,
        List<BigDecimal> takeProfitValues,
        List<Integer> cooldownHoursValues,
        List<BigDecimal> minPriceDropValues,
        List<BigDecimal> aggressiveDcaDropValues
    ) {
        this.smaPeriodValues = smaPeriodValues;
        this.positionSizeValues = positionSizeValues;
        this.smaDeviationValues = smaDeviationValues;
        this.takeProfitValues = takeProfitValues;
        this.cooldownHoursValues = cooldownHoursValues;
        this.minPriceDropValues = minPriceDropValues;
        this.aggressiveDcaDropValues = aggressiveDcaDropValues;
    }

    /**
     * Create default permutation with reasonable parameter ranges
     *
     * Generates moderate number of configurations for comprehensive testing
     */
    public static SMAOpportunisticParameterPermutation defaultPermutation() {
        List<Integer> periods = new ArrayList<>();
        for (int period = 600; period <= 2040; period += 240) {  // 600, 840, 1080, ..., 2040
            periods.add(period);
        }

        return new SMAOpportunisticParameterPermutation(
            periods,                                          // SMA: 600-2040 step 240 (7 values)
            List.of(                                         // Y: Position size
                new BigDecimal("3.0"),
                new BigDecimal("5.0"),
                new BigDecimal("7.0")                        // 3 values
            ),
            List.of(                                         // X: SMA deviation
                new BigDecimal("1.5"),
                new BigDecimal("2.0"),
                new BigDecimal("2.5")                        // 3 values
            ),
            List.of(                                         // Z: Take profit
                new BigDecimal("2.0"),
                new BigDecimal("3.0"),
                new BigDecimal("4.0")                        // 3 values
            ),
            List.of(12, 24, 48),                             // B: Cooldown (3 values)
            List.of(                                         // Min price drop
                new BigDecimal("0.5"),
                new BigDecimal("1.0"),
                new BigDecimal("1.5")                        // 3 values
            ),
            List.of(                                         // Aggressive DCA drop
                new BigDecimal("3.0"),
                new BigDecimal("5.0"),
                new BigDecimal("7.0")                        // 3 values
            )
        );
        // Total: 7 * 3 * 3 * 3 * 3 * 3 * 3 = 5103 configurations
    }

    /**
     * Create compact permutation for quick testing
     * Tests single SMA value with default other parameters
     */
    public static SMAOpportunisticParameterPermutation compactPermutation() {
        return new SMAOpportunisticParameterPermutation(
            List.of(1440),                                   // SMA: 24h only
            List.of(new BigDecimal("5.0")),                 // Y: 5% position size
            List.of(new BigDecimal("2.0")),                 // X: 2% deviation
            List.of(new BigDecimal("3.0")),                 // Z: 3% TP
            List.of(24),                                     // B: 24h cooldown
            List.of(new BigDecimal("1.0")),                 // Min price drop: 1%
            List.of(new BigDecimal("5.0"))                  // Aggressive DCA: 5%
        );
        // Total: 1 configuration (for testing)
    }

    /**
     * Generate all configuration combinations
     */
    public List<SMAOpportunisticConfig> generateConfigurations() {
        List<SMAOpportunisticConfig> configs = new ArrayList<>();

        for (Integer smaPeriod : smaPeriodValues) {
            for (BigDecimal positionSize : positionSizeValues) {
                for (BigDecimal smaDeviation : smaDeviationValues) {
                    for (BigDecimal takeProfit : takeProfitValues) {
                        for (Integer cooldown : cooldownHoursValues) {
                            for (BigDecimal minPriceDrop : minPriceDropValues) {
                                for (BigDecimal aggressiveDcaDrop : aggressiveDcaDropValues) {
                                    SMAOpportunisticConfig config = SMAOpportunisticConfig.builder()
                                        .smaPeriod(smaPeriod)
                                        .positionSizePercent(positionSize)
                                        .smaDeviationPercent(smaDeviation)
                                        .takeProfitPercent(takeProfit)
                                        .cooldownHours(cooldown)
                                        .minPriceDropPercent(minPriceDrop)
                                        .aggressiveDcaDropPercent(aggressiveDcaDrop)
                                        .build();
                                    configs.add(config);
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
     * Get number of configurations that will be generated
     */
    public int getConfigurationCount() {
        return smaPeriodValues.size()
            * positionSizeValues.size()
            * smaDeviationValues.size()
            * takeProfitValues.size()
            * cooldownHoursValues.size()
            * minPriceDropValues.size()
            * aggressiveDcaDropValues.size();
    }
}
