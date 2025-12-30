package com.tradingbot.core.metrics;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Immutable container for simulation metrics.
 * Contains all required metrics from specifications.
 */
@Value
@Builder
public class SimulationResult {
    // Basic info
    String algorithmName;
    String tradingPair;
    long startTimestamp;
    long endTimestamp;
    boolean interrupted;
    String interruptionReason;

    // Initial state
    BigDecimal initialBalance;

    // Final state
    BigDecimal finalCashBalance;
    BigDecimal finalEquity;  // Cash + open positions value
    int totalTradesExecuted;
    int openPositionsAtEnd;

    // Profit metrics
    BigDecimal profitAbsolute;
    BigDecimal profitPercentage;

    // Equity metrics (with open positions)
    BigDecimal maxEquityWithPositions;
    BigDecimal minEquityWithPositions;

    // Cash balance metrics (without open positions)
    BigDecimal maxCashBalance;
    BigDecimal minCashBalance;

    // Drawdown metrics
    BigDecimal maxPositionDrawdownPercentage;  // Max % drop in single position
    BigDecimal maxPortfolioDrawdownPercentage; // Max % drop from peak equity

    // Order history
    List<FilledOrder> filledOrders;  // Complete history of all filled orders

    /**
     * Calculate profit absolute from initial and final
     */
    public static BigDecimal calculateProfitAbsolute(BigDecimal initial, BigDecimal finalEquity) {
        return finalEquity.subtract(initial);
    }

    /**
     * Calculate profit percentage
     */
    public static BigDecimal calculateProfitPercentage(BigDecimal initial, BigDecimal finalEquity) {
        if (initial.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return finalEquity.subtract(initial)
                         .divide(initial, 6, RoundingMode.HALF_UP)
                         .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Calculate max drawdown percentage from peak
     */
    public static BigDecimal calculateMaxDrawdown(BigDecimal maxEquity, BigDecimal minEquityAfterPeak) {
        if (maxEquity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return maxEquity.subtract(minEquityAfterPeak)
                       .divide(maxEquity, 6, RoundingMode.HALF_UP)
                       .multiply(BigDecimal.valueOf(100));
    }

    @Override
    public String toString() {
        return String.format(
            """
            SimulationResult[
              Algorithm: %s
              Pair: %s
              Duration: %d ms
              Interrupted: %s%s
              Initial Balance: %.2f
              Final Equity: %.2f
              Profit: %.2f (%.2f%%)
              Trades: %d
              Max Equity: %.2f
              Min Equity: %.2f
              Max Cash: %.2f
              Min Cash: %.2f
              Max Position DD: %.2f%%
              Max Portfolio DD: %.2f%%
            ]""",
            algorithmName,
            tradingPair,
            endTimestamp - startTimestamp,
            interrupted,
            interrupted ? " - " + interruptionReason : "",
            initialBalance,
            finalEquity,
            profitAbsolute,
            profitPercentage,
            totalTradesExecuted,
            maxEquityWithPositions,
            minEquityWithPositions,
            maxCashBalance,
            minCashBalance,
            maxPositionDrawdownPercentage,
            maxPortfolioDrawdownPercentage
        );
    }
}
