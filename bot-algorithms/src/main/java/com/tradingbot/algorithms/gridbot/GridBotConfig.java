package com.tradingbot.algorithms.gridbot;

import com.tradingbot.core.algorithms.AlgorithmConfig;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Configuration for Grid Bot algorithm.
 */
@Value
@Builder
public class GridBotConfig implements AlgorithmConfig {

    /**
     * Number of grid levels
     */
    int gridLevels;

    /**
     * Distance between grid levels (percentage)
     * Example: 1.0 = 1% distance
     */
    BigDecimal gridDistancePercent;

    /**
     * Take profit percentage for each level
     * Example: 2.0 = 2% take profit
     */
    BigDecimal takeProfitPercent;

    /**
     * Percentage of portfolio to allocate to grid
     * Example: 70.0 = 70% of portfolio
     */
    BigDecimal portfolioAllocationPercent;

    @Override
    public String getConfigId() {
        return String.format("GridBot[levels=%d,dist=%.2f%%,tp=%.2f%%,alloc=%.2f%%]",
            gridLevels, gridDistancePercent, takeProfitPercent, portfolioAllocationPercent);
    }

    /**
     * Create default config for single simulation:
     * - 20 levels
     * - 1% distance
     * - 2% take profit
     * - 70% portfolio allocation
     */
    public static GridBotConfig defaultConfig() {
        return GridBotConfig.builder()
            .gridLevels(20)
            .gridDistancePercent(new BigDecimal("1.0"))
            .takeProfitPercent(new BigDecimal("2.0"))
            .portfolioAllocationPercent(new BigDecimal("70.0"))
            .build();
    }

    /**
     * Validate configuration
     */
    public void validate() {
        if (gridLevels <= 0) {
            throw new IllegalArgumentException("Grid levels must be positive");
        }
        if (gridDistancePercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Grid distance must be positive");
        }
        if (takeProfitPercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Take profit must be positive");
        }
        if (portfolioAllocationPercent.compareTo(BigDecimal.ZERO) <= 0 ||
            portfolioAllocationPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Portfolio allocation must be between 0 and 100");
        }
    }
}
