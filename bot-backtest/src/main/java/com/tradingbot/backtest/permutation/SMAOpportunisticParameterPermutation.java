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
     * Generate range of BigDecimal values
     * @param start Start value (inclusive)
     * @param end End value (inclusive)
     * @param step Step between values
     * @return List of BigDecimal values
     */
    private static List<BigDecimal> range(double start, double end, double step) {
        List<BigDecimal> values = new ArrayList<>();
        for (double value = start; value <= end + 0.001; value += step) {  // +0.001 for floating point tolerance
            values.add(new BigDecimal(String.valueOf(value)));
        }
        return values;
    }

    /**
     * Generate range of Integer values
     * @param start Start value (inclusive)
     * @param end End value (inclusive)
     * @param step Step between values
     * @return List of Integer values
     */
    private static List<Integer> rangeInt(int start, int end, int step) {
        List<Integer> values = new ArrayList<>();
        for (int value = start; value <= end; value += step) {
            values.add(value);
        }
        return values;
    }

    /**
     * Create default permutation with reasonable parameter ranges
     *
     * Generates moderate number of configurations for comprehensive testing
     */
    public static SMAOpportunisticParameterPermutation defaultPermutation() {
        return new SMAOpportunisticParameterPermutation(
            rangeInt(600, 2040, 240),        // SMA: 600-2040 step 240 (7 values)
            range(3.0, 7.0, 2.0),            // Y: Position size 3%-7% step 2% (3 values)
            range(1.5, 2.5, 0.5),            // X: SMA deviation 1.5%-2.5% step 0.5% (3 values)
            range(2.0, 4.0, 1.0),            // Z: Take profit 2%-4% step 1% (3 values)
            rangeInt(12, 48, 12),            // B: Cooldown 12h-48h step 12h (4 values)
            range(0.5, 1.5, 0.5),            // Min price drop 0.5%-1.5% step 0.5% (3 values)
            range(3.0, 7.0, 2.0)             // Aggressive DCA 3%-7% step 2% (3 values)
        );
        // Total: 7 * 3 * 3 * 3 * 4 * 3 * 3 = 6804 configurations
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
     * Create quick permutation for faster testing
     * Reduced parameter space for quicker iteration
     */
    public static SMAOpportunisticParameterPermutation quickPermutation() {
        return new SMAOpportunisticParameterPermutation(
            rangeInt(600, 1800, 400),        // SMA: 600-1800 step 400 (4 values)
            range(5.0, 7.0, 2.0),            // Y: Position size 5%-7% step 2% (2 values)
            range(1.5, 2.5, 0.5),            // X: SMA deviation 1.5%-2.5% step 0.5% (3 values)
            range(2.0, 4.0, 1.0),            // Z: Take profit 2%-4% step 1% (3 values)
            rangeInt(24, 48, 24),            // B: Cooldown 24h-48h step 24h (2 values)
            range(0.5, 1.5, 0.5),            // Min price drop 0.5%-1.5% step 0.5% (3 values)
            range(3.0, 7.0, 2.0)             // Aggressive DCA 3%-7% step 2% (3 values)
        );
        // Total: 4 * 2 * 3 * 3 * 2 * 3 * 3 = 1296 configurations
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
