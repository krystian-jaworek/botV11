package com.tradingbot.core.metrics;

import com.tradingbot.core.models.Portfolio;
import com.tradingbot.core.models.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility class for calculating trading metrics during simulation.
 */
public class MetricsCalculator {

    /**
     * Track max position drawdown during simulation.
     * This class helps track the maximum percentage drop for each position.
     */
    public static class PositionDrawdownTracker {
        private BigDecimal maxPositionDrawdown = BigDecimal.ZERO;

        /**
         * Update tracker with current position state
         */
        public void updatePosition(Position position, BigDecimal currentPrice) {
            BigDecimal pnlPercentage = position.getUnrealizedPnLPercentage(currentPrice);

            // If PnL is negative, check if it's worse than our max drawdown
            if (pnlPercentage.compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal absoluteDrawdown = pnlPercentage.abs();
                if (absoluteDrawdown.compareTo(maxPositionDrawdown) > 0) {
                    maxPositionDrawdown = absoluteDrawdown;
                }
            }
        }

        public BigDecimal getMaxDrawdown() {
            return maxPositionDrawdown;
        }
    }

    /**
     * Track portfolio drawdown (from peak equity).
     * This helps calculate max drawdown percentage.
     */
    public static class PortfolioDrawdownTracker {
        private BigDecimal peakEquity;
        private BigDecimal maxDrawdownPercentage = BigDecimal.ZERO;

        public PortfolioDrawdownTracker(BigDecimal initialEquity) {
            this.peakEquity = initialEquity;
        }

        /**
         * Update tracker with current equity
         */
        public void updateEquity(BigDecimal currentEquity) {
            // Update peak if we reached new high
            if (currentEquity.compareTo(peakEquity) > 0) {
                peakEquity = currentEquity;
            }

            // Calculate current drawdown from peak
            if (peakEquity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentDrawdown = peakEquity.subtract(currentEquity)
                    .divide(peakEquity, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

                // Update max drawdown if current is worse
                if (currentDrawdown.compareTo(maxDrawdownPercentage) > 0) {
                    maxDrawdownPercentage = currentDrawdown;
                }
            }
        }

        public BigDecimal getMaxDrawdownPercentage() {
            return maxDrawdownPercentage;
        }

        public BigDecimal getPeakEquity() {
            return peakEquity;
        }
    }

    /**
     * Build simulation result from portfolio and trackers
     */
    public static SimulationResult buildResult(
        String algorithmName,
        String tradingPair,
        long startTimestamp,
        long endTimestamp,
        BigDecimal initialBalance,
        Portfolio portfolio,
        BigDecimal finalPrice,
        PositionDrawdownTracker positionTracker,
        PortfolioDrawdownTracker portfolioTracker,
        boolean interrupted,
        String interruptionReason
    ) {
        BigDecimal finalEquity = portfolio.getEquity(finalPrice);
        BigDecimal profitAbsolute = SimulationResult.calculateProfitAbsolute(initialBalance, finalEquity);
        BigDecimal profitPercentage = SimulationResult.calculateProfitPercentage(initialBalance, finalEquity);

        int totalTrades = portfolio.getClosedPositionCount();

        return SimulationResult.builder()
            .algorithmName(algorithmName)
            .tradingPair(tradingPair)
            .startTimestamp(startTimestamp)
            .endTimestamp(endTimestamp)
            .interrupted(interrupted)
            .interruptionReason(interruptionReason)
            .initialBalance(initialBalance)
            .finalCashBalance(portfolio.getCashBalance())
            .finalEquity(finalEquity)
            .totalTradesExecuted(totalTrades)
            .openPositionsAtEnd(portfolio.getOpenPositionCount())
            .profitAbsolute(profitAbsolute)
            .profitPercentage(profitPercentage)
            .maxEquityWithPositions(portfolio.getMaxEquity())
            .minEquityWithPositions(portfolio.getMinEquity())
            .maxCashBalance(portfolio.getMaxCashBalance())
            .minCashBalance(portfolio.getMinCashBalance())
            .maxPositionDrawdownPercentage(positionTracker.getMaxDrawdown())
            .maxPortfolioDrawdownPercentage(portfolioTracker.getMaxDrawdownPercentage())
            .build();
    }
}
