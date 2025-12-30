package com.tradingbot.backtest.permutation;

import com.tradingbot.algorithms.dynamicgrid.DynamicGridConfig;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates all permutations of DynamicGrid parameters.
 */
@Value
@Builder
public class DynamicGridParameterPermutation {
    ParameterRange<Integer> gridLevelsRange;
    ParameterRange<BigDecimal> gridSpacingRange;
    ParameterRange<BigDecimal> takeProfitRange;
    ParameterRange<BigDecimal> positionSizeRange;
    ParameterRange<BigDecimal> topTriggerRange;
    List<Boolean> useFixedSizeOptions;

    /**
     * Generate all possible DynamicGrid configurations from parameter ranges
     */
    public List<DynamicGridConfig> generateConfigurations() {
        List<DynamicGridConfig> configurations = new ArrayList<>();

        List<Integer> gridLevels = gridLevelsRange.generateValues();
        List<BigDecimal> gridSpacings = gridSpacingRange.generateValues();
        List<BigDecimal> takeProfits = takeProfitRange.generateValues();
        List<BigDecimal> positionSizes = positionSizeRange.generateValues();
        List<BigDecimal> topTriggers = topTriggerRange.generateValues();

        // Generate all combinations
        for (Integer levels : gridLevels) {
            for (BigDecimal spacing : gridSpacings) {
                for (BigDecimal tp : takeProfits) {
                    for (BigDecimal size : positionSizes) {
                        for (BigDecimal trigger : topTriggers) {
                            for (Boolean fixedSize : useFixedSizeOptions) {
                                DynamicGridConfig config = DynamicGridConfig.builder()
                                    .gridLevels(levels)
                                    .gridSpacingPercent(spacing)
                                    .takeProfitPercent(tp)
                                    .positionSizePercent(size)
                                    .topTriggerPercent(trigger)
                                    .useFixedPositionSize(fixedSize)
                                    .build();

                                configurations.add(config);
                            }
                        }
                    }
                }
            }
        }

        return configurations;
    }

    /**
     * Get total number of configurations that will be generated
     */
    public long getTotalConfigurations() {
        long count = 1;
        count *= gridLevelsRange.generateValues().size();
        count *= gridSpacingRange.generateValues().size();
        count *= takeProfitRange.generateValues().size();
        count *= positionSizeRange.generateValues().size();
        count *= topTriggerRange.generateValues().size();
        count *= useFixedSizeOptions.size();
        return count;
    }

    /**
     * Create a default permutation for testing
     *
     * Example ranges:
     * - Grid levels: 10-20 (step 5) = 3 values
     * - Grid spacing: 0.5-2.0% (step 0.5) = 4 values
     * - Take profit: 1.0-3.0% (step 0.5) = 5 values
     * - Position size: 5-15% (step 5) = 3 values
     * - Top trigger: 0.3-0.7% (step 0.2) = 3 values
     * - Fixed size: [true, false] = 2 values
     *
     * Total: 3 * 4 * 5 * 3 * 3 * 2 = 1,080 configurations
     */
    public static DynamicGridParameterPermutation defaultPermutation() {
        return DynamicGridParameterPermutation.builder()
            .gridLevelsRange(ParameterRange.intRange("gridLevels", 10, 20, 5))
            .gridSpacingRange(ParameterRange.decimalRange("gridSpacing", "0.5", "2.0", "0.5"))
            .takeProfitRange(ParameterRange.decimalRange("takeProfit", "1.0", "3.0", "0.5"))
            .positionSizeRange(ParameterRange.decimalRange("positionSize", "5", "15", "5"))
            .topTriggerRange(ParameterRange.decimalRange("topTrigger", "0.3", "0.7", "0.2"))
            .useFixedSizeOptions(List.of(false, true))
            .build();
    }
}
