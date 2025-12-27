package com.tradingbot.backtest.permutation;

import com.tradingbot.algorithms.gridbot.GridBotConfig;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates all permutations of GridBot parameters.
 */
@Value
@Builder
public class GridBotParameterPermutation {
    ParameterRange<Integer> gridLevelsRange;
    ParameterRange<BigDecimal> gridDistanceRange;
    ParameterRange<BigDecimal> takeProfitRange;
    ParameterRange<BigDecimal> portfolioAllocationRange;

    /**
     * Generate all possible GridBot configurations from parameter ranges
     */
    public List<GridBotConfig> generateConfigurations() {
        List<GridBotConfig> configurations = new ArrayList<>();

        List<Integer> gridLevels = gridLevelsRange.generateValues();
        List<BigDecimal> gridDistances = gridDistanceRange.generateValues();
        List<BigDecimal> takeProfits = takeProfitRange.generateValues();
        List<BigDecimal> allocations = portfolioAllocationRange.generateValues();

        // Generate all combinations
        for (Integer levels : gridLevels) {
            for (BigDecimal distance : gridDistances) {
                for (BigDecimal tp : takeProfits) {
                    for (BigDecimal allocation : allocations) {
                        GridBotConfig config = GridBotConfig.builder()
                            .gridLevels(levels)
                            .gridDistancePercent(distance)
                            .takeProfitPercent(tp)
                            .portfolioAllocationPercent(allocation)
                            .build();

                        configurations.add(config);
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
        count *= gridDistanceRange.generateValues().size();
        count *= takeProfitRange.generateValues().size();
        count *= portfolioAllocationRange.generateValues().size();
        return count;
    }

    /**
     * Create a default permutation for testing
     * Example ranges:
     * - Grid levels: 10-30 (step 5)
     * - Grid distance: 0.5-2.0% (step 0.5)
     * - Take profit: 1.0-3.0% (step 0.5)
     * - Portfolio allocation: 60-80% (step 10)
     *
     * Total: 5 * 4 * 5 * 3 = 300 configurations
     */
    public static GridBotParameterPermutation defaultPermutation() {
        return GridBotParameterPermutation.builder()
            .gridLevelsRange(ParameterRange.intRange("gridLevels", 10, 30, 5))
            .gridDistanceRange(ParameterRange.decimalRange("gridDistance", "0.5", "2.0", "0.5"))
            .takeProfitRange(ParameterRange.decimalRange("takeProfit", "1.0", "3.0", "0.5"))
            .portfolioAllocationRange(ParameterRange.decimalRange("allocation", "60", "80", "10"))
            .build();
    }
}
