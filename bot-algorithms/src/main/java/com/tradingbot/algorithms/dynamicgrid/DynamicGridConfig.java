package com.tradingbot.algorithms.dynamicgrid;

import com.tradingbot.core.algorithms.AlgorithmConfig;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Configuration for Dynamic Grid Trading algorithm.
 *
 * Dynamic Grid maintains a grid of price levels that can shift:
 * - Bottom breach: closes oldest position, adds new level below
 * - Top expansion: closes oldest position, expands grid upward
 */
@Value
@Builder
public class DynamicGridConfig implements AlgorithmConfig {

    /**
     * Total number of grid levels
     */
    int gridLevels;

    /**
     * Spacing between grid levels (percentage)
     * Example: 1.0 = 1% distance between levels
     */
    BigDecimal gridSpacingPercent;

    /**
     * Take profit percentage for each position
     * Example: 2.0 = 2% profit target from entry
     */
    BigDecimal takeProfitPercent;

    /**
     * Total percentage of portfolio allocated to the entire grid
     * This amount is divided equally among all grid levels
     * Example: 80.0 = 80% of portfolio divided by gridLevels
     */
    BigDecimal totalGridCapitalPercent;

    /**
     * Trigger percentage above top level to expand grid upward
     * Example: 5.0 = expand when price > top_level × 1.05
     */
    BigDecimal topTriggerPercent;

    @Override
    public String getConfigId() {
        return String.format(
            "DynamicGrid[levels=%d,spacing=%.2f%%,tp=%.2f%%,gridCapital=%.2f%%,trigger=%.2f%%]",
            gridLevels,
            gridSpacingPercent,
            takeProfitPercent,
            totalGridCapitalPercent,
            topTriggerPercent
        );
    }

    /**
     * Create default config for testing
     */
    public static DynamicGridConfig defaultConfig() {
        return DynamicGridConfig.builder()
            .gridLevels(10)
            .gridSpacingPercent(new BigDecimal("1.0"))
            .takeProfitPercent(new BigDecimal("2.0"))
            .totalGridCapitalPercent(new BigDecimal("80.0"))
            .topTriggerPercent(new BigDecimal("5.0"))
            .build();
    }

    /**
     * Validate configuration
     */
    public void validate() {
        if (gridLevels < 3) {
            throw new IllegalArgumentException("Grid levels must be at least 3");
        }
        if (gridSpacingPercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Grid spacing must be positive");
        }
        if (takeProfitPercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Take profit must be positive");
        }
        if (totalGridCapitalPercent.compareTo(BigDecimal.ZERO) <= 0 ||
            totalGridCapitalPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Total grid capital must be between 0 and 100");
        }
        if (topTriggerPercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Top trigger must be non-negative");
        }
    }
}
