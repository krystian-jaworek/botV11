package com.tradingbot.algorithms.smartdca;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.List;

/**
 * Aggregates multiple DCA parcels into a single logical position.
 * Tracks total quantity, average entry price, and manages FIFO closing.
 */
public class DCAPosition {

    @Getter
    private final LinkedList<DCAParcel> parcels;  // FIFO queue

    @Getter
    private BigDecimal totalQuantity;

    @Getter
    private BigDecimal totalInvested;

    // Peak tracking for trailing stop
    @Getter
    private BigDecimal peakPrice;

    // Breakeven stop activation
    @Getter
    private boolean breakevenStopActive;

    // Trailing stop activation
    @Getter
    private boolean trailingStopActive;

    // Staged exits tracking - which stages have been triggered
    @Getter
    private final List<Integer> triggeredStages;

    public DCAPosition() {
        this.parcels = new LinkedList<>();
        this.totalQuantity = BigDecimal.ZERO;
        this.totalInvested = BigDecimal.ZERO;
        this.peakPrice = BigDecimal.ZERO;
        this.breakevenStopActive = false;
        this.trailingStopActive = false;
        this.triggeredStages = new LinkedList<>();
    }

    /**
     * Add a new parcel to the position
     */
    public void addParcel(DCAParcel parcel) {
        parcels.add(parcel);
        totalQuantity = totalQuantity.add(parcel.getQuantity());
        totalInvested = totalInvested.add(parcel.getInvestedAmount());
    }

    /**
     * Calculate average entry price across all parcels
     */
    public BigDecimal getAverageEntryPrice() {
        if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalInvested.divide(totalQuantity, 8, RoundingMode.HALF_UP);
    }

    /**
     * Calculate current profit percentage from average entry price
     */
    public BigDecimal getCurrentProfitPct(BigDecimal currentPrice) {
        BigDecimal avgEntry = getAverageEntryPrice();
        if (avgEntry.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(avgEntry)
            .divide(avgEntry, 8, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    /**
     * Calculate unrealized P&L at current price
     */
    public BigDecimal getUnrealizedPnL(BigDecimal currentPrice) {
        return totalQuantity.multiply(currentPrice.subtract(getAverageEntryPrice()));
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
     * Close a percentage of the position using FIFO.
     * Returns the realized profit and the quantity closed.
     *
     * @param percentageToClose Percentage of total quantity to close (e.g., 15 for 15%)
     * @param closePrice Price at which to close
     * @return CloseResult with realized profit and quantity closed
     */
    public CloseResult closePercentage(BigDecimal percentageToClose, BigDecimal closePrice) {
        if (percentageToClose.compareTo(BigDecimal.ZERO) <= 0 ||
            percentageToClose.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        }

        BigDecimal targetQuantity = totalQuantity
            .multiply(percentageToClose)
            .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);

        return closeFifo(targetQuantity, closePrice);
    }

    /**
     * Close specific quantity using FIFO
     */
    private CloseResult closeFifo(BigDecimal quantityToClose, BigDecimal closePrice) {
        BigDecimal remainingToClose = quantityToClose;
        BigDecimal totalRealizedPnL = BigDecimal.ZERO;
        BigDecimal totalClosedQuantity = BigDecimal.ZERO;
        BigDecimal totalInvestedClosed = BigDecimal.ZERO;

        while (remainingToClose.compareTo(BigDecimal.ZERO) > 0 && !parcels.isEmpty()) {
            DCAParcel oldest = parcels.getFirst();

            if (oldest.getQuantity().compareTo(remainingToClose) <= 0) {
                // Close entire parcel
                BigDecimal pnl = oldest.getQuantity()
                    .multiply(closePrice.subtract(oldest.getEntryPrice()));
                totalRealizedPnL = totalRealizedPnL.add(pnl);
                totalClosedQuantity = totalClosedQuantity.add(oldest.getQuantity());
                totalInvestedClosed = totalInvestedClosed.add(oldest.getInvestedAmount());
                remainingToClose = remainingToClose.subtract(oldest.getQuantity());
                parcels.removeFirst();
            } else {
                // Partially close parcel
                BigDecimal pnl = remainingToClose
                    .multiply(closePrice.subtract(oldest.getEntryPrice()));
                totalRealizedPnL = totalRealizedPnL.add(pnl);
                totalClosedQuantity = totalClosedQuantity.add(remainingToClose);

                BigDecimal investedPortion = oldest.getInvestedAmount()
                    .multiply(remainingToClose)
                    .divide(oldest.getQuantity(), 8, RoundingMode.HALF_UP);
                totalInvestedClosed = totalInvestedClosed.add(investedPortion);

                // Create updated parcel with reduced quantity
                BigDecimal newQuantity = oldest.getQuantity().subtract(remainingToClose);
                BigDecimal newInvested = oldest.getInvestedAmount().subtract(investedPortion);

                parcels.removeFirst();
                parcels.addFirst(DCAParcel.builder()
                    .id(oldest.getId())
                    .timestamp(oldest.getTimestamp())
                    .entryPrice(oldest.getEntryPrice())
                    .quantity(newQuantity)
                    .investedAmount(newInvested)
                    .tierName(oldest.getTierName())
                    .build());

                remainingToClose = BigDecimal.ZERO;
            }
        }

        // Update totals
        totalQuantity = totalQuantity.subtract(totalClosedQuantity);
        totalInvested = totalInvested.subtract(totalInvestedClosed);

        return new CloseResult(totalRealizedPnL, totalClosedQuantity, totalInvestedClosed);
    }

    /**
     * Check if position is empty
     */
    public boolean isEmpty() {
        return parcels.isEmpty() || totalQuantity.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Get the timestamp of the most recent parcel
     */
    public long getLastBuyTimestamp() {
        if (parcels.isEmpty()) {
            return 0;
        }
        return parcels.getLast().getTimestamp();
    }

    /**
     * Get number of parcels
     */
    public int getParcelCount() {
        return parcels.size();
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
