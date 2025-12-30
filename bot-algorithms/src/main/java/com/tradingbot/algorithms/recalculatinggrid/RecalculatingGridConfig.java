package com.tradingbot.algorithms.recalculatinggrid;

import com.tradingbot.core.algorithms.AlgorithmConfig;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Configuration for Recalculating Grid Trading algorithm.
 *
 * RecalculatingGrid maintains a grid that completely resets when price moves beyond thresholds:
 * - Top reset: closes all positions (with profit) and recalculates grid from current price
 * - Bottom reset: hard reset, closes all positions (with loss) and recalculates grid from current price
 */
@Value
@Builder
public class RecalculatingGridConfig implements AlgorithmConfig {

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
     * Trigger percentage above top level to reset grid
     * Example: 10.0 = reset when price > top_level × 1.10
     */
    BigDecimal topResetTriggerPercent;

    /**
     * Trigger percentage below bottom level for hard reset
     * Example: 10.0 = hard reset when price < bottom_level × 0.90
     */
    BigDecimal bottomResetTriggerPercent;

    @Override
    public String getConfigId() {
        return String.format(
            "RecalculatingGrid[levels=%d,spacing=%.2f%%,tp=%.2f%%,gridCapital=%.2f%%,topReset=%.2f%%,bottomReset=%.2f%%]",
            gridLevels,
            gridSpacingPercent,
            takeProfitPercent,
            totalGridCapitalPercent,
            topResetTriggerPercent,
            bottomResetTriggerPercent
        );
    }

    /**
     * Create default config for testing
     */
    public static RecalculatingGridConfig defaultConfig() {
        return RecalculatingGridConfig.builder()
            .gridLevels(10)
            .gridSpacingPercent(new BigDecimal("1.0"))
            .takeProfitPercent(new BigDecimal("2.0"))
            .totalGridCapitalPercent(new BigDecimal("80.0"))
            .topResetTriggerPercent(new BigDecimal("10.0"))
            .bottomResetTriggerPercent(new BigDecimal("10.0"))
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
        if (topResetTriggerPercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Top reset trigger must be non-negative");
        }
        if (bottomResetTriggerPercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Bottom reset trigger must be non-negative");
        }
    }
}
