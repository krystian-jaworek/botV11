package com.tradingbot.algorithms.smartdca;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple DCA position tracker using weighted average entry price.
 * Tracks one logical position that grows with each buy.
 */
public class DCAPosition {

    @Getter
    private BigDecimal totalQuantity;

    @Getter
    private BigDecimal avgEntryPrice;

    @Getter
    private BigDecimal totalInvested;

    // Peak tracking for trailing stop
    @Getter
    private BigDecimal peakPrice;

    // Stop activation flags
    @Getter
    private boolean breakevenStopActive;

    @Getter
    private boolean trailingStopActive;

    // Staged exits tracking - which stages have been triggered
    @Getter
    private final List<Integer> triggeredStages;

    // Track all Position IDs created by buys (for closing via BacktestEngine)
    @Getter
    private final List<String> positionIds;

    // Track last buy details
    @Getter
    private long lastBuyTimestamp;

    @Getter
    private BigDecimal lastBuyPrice;

    public DCAPosition() {
        this.totalQuantity = BigDecimal.ZERO;
        this.avgEntryPrice = BigDecimal.ZERO;
        this.totalInvested = BigDecimal.ZERO;
        this.peakPrice = BigDecimal.ZERO;
        this.breakevenStopActive = false;
        this.trailingStopActive = false;
        this.triggeredStages = new ArrayList<>();
        this.positionIds = new ArrayList<>();
        this.lastBuyTimestamp = 0;
        this.lastBuyPrice = BigDecimal.ZERO;
    }

    /**
     * Add a purchase to the position (dokup).
     * Recalculates weighted average entry price.
     */
    public void addPurchase(BigDecimal quantity, BigDecimal price, long timestamp, String positionId) {
        BigDecimal invested = quantity.multiply(price);

        totalInvested = totalInvested.add(invested);
        totalQuantity = totalQuantity.add(quantity);

        // Recalculate weighted average entry price
        if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
            avgEntryPrice = totalInvested.divide(totalQuantity, 8, RoundingMode.HALF_UP);
        }

        // Track position ID for later closing
        positionIds.add(positionId);

        // Update last buy info
        lastBuyTimestamp = timestamp;
        lastBuyPrice = price;
    }

    /**
     * Calculate current profit percentage from average entry price
     */
    public BigDecimal getCurrentProfitPct(BigDecimal currentPrice) {
        if (avgEntryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(avgEntryPrice)
            .divide(avgEntryPrice, 8, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    /**
     * Calculate unrealized P&L at current price
     */
    public BigDecimal getUnrealizedPnL(BigDecimal currentPrice) {
        return totalQuantity.multiply(currentPrice.subtract(avgEntryPrice));
    }

    /**
     * Update peak price for trailing stop
     */
    public void updatePeakPrice(BigDecimal currentPrice) {
        if (currentPrice.compareTo(peakPrice) > 0) {
            peakPrice = currentPrice;
        }
    }

    /**
     * Activate breakeven stop
     */
    public void activateBreakevenStop() {
        this.breakevenStopActive = true;
    }

    /**
     * Activate trailing stop
     */
    public void activateTrailingStop() {
        this.trailingStopActive = true;
    }

    /**
     * Mark a staged exit as triggered
     */
    public void markStageTriggered(int stageIndex) {
        if (!triggeredStages.contains(stageIndex)) {
            triggeredStages.add(stageIndex);
        }
    }

    /**
     * Check if a staged exit has been triggered
     */
    public boolean isStageTriggered(int stageIndex) {
        return triggeredStages.contains(stageIndex);
    }

    /**
     * Close an exact quantity of the position.
     * More precise than closePercentage() - avoids floating point errors.
     *
     * @param quantityToClose Exact quantity to close
     * @param closePrice Price at which to close
     * @return CloseResult with realized profit and quantity closed
     */
    public CloseResult closeByQuantity(BigDecimal quantityToClose, BigDecimal closePrice) {
        if (quantityToClose.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity to close must be positive");
        }

        if (quantityToClose.compareTo(totalQuantity) > 0) {
            throw new IllegalArgumentException(
                String.format("Cannot close more than available: %s > %s", quantityToClose, totalQuantity));
        }

        // Calculate proportional invested amount (maintains weighted average)
        BigDecimal percentageOfTotal = quantityToClose
            .divide(totalQuantity, 10, RoundingMode.HALF_UP);
        BigDecimal investedToClose = totalInvested.multiply(percentageOfTotal);

        // Calculate realized P&L from average entry price
        BigDecimal realizedPnL = quantityToClose.multiply(closePrice.subtract(avgEntryPrice));

        // Update totals
        totalQuantity = totalQuantity.subtract(quantityToClose);
        totalInvested = totalInvested.subtract(investedToClose);

        // Recalculate average entry (should remain the same for remaining quantity)
        if (totalQuantity.compareTo(new BigDecimal("0.00000001")) > 0) {
            avgEntryPrice = totalInvested.divide(totalQuantity, 8, RoundingMode.HALF_UP);
        } else {
            // Close enough to zero - clear everything
            avgEntryPrice = BigDecimal.ZERO;
            totalQuantity = BigDecimal.ZERO;
            totalInvested = BigDecimal.ZERO;
        }

        return new CloseResult(realizedPnL, quantityToClose, investedToClose);
    }

    /**
     * Close a percentage of the position.
     * Returns the realized profit and quantity closed.
     *
     * @param percentageToClose Percentage of total quantity to close (e.g., 15 for 15%)
     * @param closePrice Price at which to close
     * @return CloseResult with realized profit and quantity closed
     * @deprecated Use closeByQuantity() for better precision
     */
    public CloseResult closePercentage(BigDecimal percentageToClose, BigDecimal closePrice) {
        if (percentageToClose.compareTo(BigDecimal.ZERO) <= 0 ||
            percentageToClose.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        }

        // Calculate quantity to close
        BigDecimal quantityToClose = totalQuantity
            .multiply(percentageToClose)
            .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);

        // Delegate to closeByQuantity for actual closing
        return closeByQuantity(quantityToClose, closePrice);
    }

    /**
     * Check if position is empty
     */
    public boolean isEmpty() {
        return totalQuantity.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Get number of underlying Position objects
     */
    public int getPositionCount() {
        return positionIds.size();
    }

    /**
     * Get the oldest position ID (for FIFO closing via engine)
     */
    public String getOldestPositionId() {
        if (positionIds.isEmpty()) {
            return null;
        }
        return positionIds.get(0);
    }

    /**
     * Remove a position ID after it's been closed
     */
    public void removePositionId(String positionId) {
        positionIds.remove(positionId);
    }

    /**
     * Result of closing a portion of the position
     */
    public static class CloseResult {
        @Getter
        private final BigDecimal realizedPnL;

        @Getter
        private final BigDecimal quantityClosed;

        @Getter
        private final BigDecimal investedClosed;

        public CloseResult(BigDecimal realizedPnL, BigDecimal quantityClosed, BigDecimal investedClosed) {
            this.realizedPnL = realizedPnL;
            this.quantityClosed = quantityClosed;
            this.investedClosed = investedClosed;
        }
    }
}
