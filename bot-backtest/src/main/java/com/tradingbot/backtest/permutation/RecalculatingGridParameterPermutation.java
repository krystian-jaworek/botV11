package com.tradingbot.backtest.permutation;

import com.tradingbot.algorithms.recalculatinggrid.RecalculatingGridConfig;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates all permutations of RecalculatingGrid parameters.
 */
@Value
@Builder
public class RecalculatingGridParameterPermutation {
    ParameterRange<Integer> gridLevelsRange;
    ParameterRange<BigDecimal> gridSpacingRange;
    ParameterRange<BigDecimal> takeProfitRange;
    ParameterRange<BigDecimal> totalGridCapitalRange;
    ParameterRange<BigDecimal> topResetTriggerRange;
    ParameterRange<BigDecimal> bottomResetTriggerRange;

    /**
     * Generate all possible RecalculatingGrid configurations from parameter ranges
     */
    public List<RecalculatingGridConfig> generateConfigurations() {
        List<RecalculatingGridConfig> configurations = new ArrayList<>();

        List<Integer> gridLevels = gridLevelsRange.generateValues();
        List<BigDecimal> gridSpacings = gridSpacingRange.generateValues();
        List<BigDecimal> takeProfits = takeProfitRange.generateValues();
        List<BigDecimal> totalGridCapitals = totalGridCapitalRange.generateValues();
        List<BigDecimal> topResetTriggers = topResetTriggerRange.generateValues();
        List<BigDecimal> bottomResetTriggers = bottomResetTriggerRange.generateValues();

        // Generate all combinations
        for (Integer levels : gridLevels) {
            for (BigDecimal spacing : gridSpacings) {
                for (BigDecimal tp : takeProfits) {
                    for (BigDecimal gridCapital : totalGridCapitals) {
                        for (BigDecimal topReset : topResetTriggers) {
                            for (BigDecimal bottomReset : bottomResetTriggers) {
                                RecalculatingGridConfig config = RecalculatingGridConfig.builder()
                                    .gridLevels(levels)
                                    .gridSpacingPercent(spacing)
                                    .takeProfitPercent(tp)
                                    .totalGridCapitalPercent(gridCapital)
                                    .topResetTriggerPercent(topReset)
                                    .bottomResetTriggerPercent(bottomReset)
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
        count *= totalGridCapitalRange.generateValues().size();
        count *= topResetTriggerRange.generateValues().size();
        count *= bottomResetTriggerRange.generateValues().size();
        return count;
    }

    /**
     * Create a default permutation for testing
     *
     * Example ranges:
     * - Grid levels: 10-20 (step 5) = 3 values
     * - Grid spacing: 0.5-2.0% (step 0.5) = 4 values
     * - Take profit: 1.0-3.0% (step 0.5) = 5 values
     * - Total grid capital: 60-90% (step 15) = 3 values
     * - Top reset trigger: 5-15% (step 5) = 3 values
     * - Bottom reset trigger: 5-15% (step 5) = 3 values
     *
     * Total: 3 * 4 * 5 * 3 * 3 * 3 = 1,620 configurations
     */
    public static RecalculatingGridParameterPermutation defaultPermutation() {
        return RecalculatingGridParameterPermutation.builder()
            .gridLevelsRange(ParameterRange.intRange("gridLevels", 10, 20, 5))
            .gridSpacingRange(ParameterRange.decimalRange("gridSpacing", "0.5", "2.0", "0.5"))
            .takeProfitRange(ParameterRange.decimalRange("takeProfit", "1.0", "3.0", "0.5"))
            .totalGridCapitalRange(ParameterRange.decimalRange("totalGridCapital", "60", "90", "15"))
            .topResetTriggerRange(ParameterRange.decimalRange("topResetTrigger", "5.0", "15.0", "5.0"))
            .bottomResetTriggerRange(ParameterRange.decimalRange("bottomResetTrigger", "5.0", "15.0", "5.0"))
            .build();
    }
}
