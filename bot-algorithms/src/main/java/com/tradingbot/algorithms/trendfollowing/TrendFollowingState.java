package com.tradingbot.algorithms.trendfollowing;

import com.tradingbot.core.algorithms.AlgorithmState;
import com.tradingbot.core.indicators.MACD.MACDResult;
import com.tradingbot.core.indicators.SwingDetector.SwingPoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Internal state for TrendFollowing algorithm.
 *
 * Stores calculated indicators and position tracking information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendFollowingState {

    // ========== Indicator Caches (Primary Timeframe) ==========
    @Builder.Default
    private List<BigDecimal> emaFastPrimary = new ArrayList<>();

    @Builder.Default
    private List<BigDecimal> emaSlowPrimary = new ArrayList<>();

    @Builder.Default
    private List<MACDResult> macdPrimary = new ArrayList<>();

    @Builder.Default
    private List<SwingPoint> swingLowsPrimary = new ArrayList<>();

    // ========== Indicator Caches (Secondary Timeframe) ==========
    @Builder.Default
    private List<BigDecimal> emaFastSecondary = new ArrayList<>();

    @Builder.Default
    private List<BigDecimal> emaSlowSecondary = new ArrayList<>();

    @Builder.Default
    private List<MACDResult> macdSecondary = new ArrayList<>();

    // ========== Position Tracking ==========
    private String currentPositionId;
    private BigDecimal entryPrice;
    private BigDecimal initialQuantity;
    private BigDecimal remainingQuantity;

    // ========== Take Profit Tracking ==========
    @Builder.Default
    private Set<Integer> tpLevelsHit = new HashSet<>();  // [0, 1, 2] for 3 TP levels

    @Builder.Default
    private List<BigDecimal> dynamicTpPrices = new ArrayList<>();  // Dynamically adjusted TP prices

    // ========== Trailing Stop Tracking ==========
    private BigDecimal trailingStopPrice;
    private boolean trailingStopActive;
    private BigDecimal highestPriceSinceEntry;  // For trailing stop calculation

    // ========== Candle Tracking ==========
    private int currentPrimaryCandleIndex;
    private int currentSecondaryCandleIndex;

    /**
     * Check if there is an open position.
     */
    public boolean hasOpenPosition() {
        return currentPositionId != null && remainingQuantity != null &&
            remainingQuantity.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Reset position state after full close.
     */
    public void resetPosition() {
        currentPositionId = null;
        entryPrice = null;
        initialQuantity = null;
        remainingQuantity = null;
        tpLevelsHit.clear();
        dynamicTpPrices.clear();
        trailingStopPrice = null;
        trailingStopActive = false;
        highestPriceSinceEntry = null;
    }

    /**
     * Initialize position tracking.
     */
    public void initializePosition(
        String positionId,
        BigDecimal entry,
        BigDecimal quantity,
        List<BigDecimal> initialTpPrices
    ) {
        this.currentPositionId = positionId;
        this.entryPrice = entry;
        this.initialQuantity = quantity;
        this.remainingQuantity = quantity;
        this.tpLevelsHit = new HashSet<>();
        this.dynamicTpPrices = new ArrayList<>(initialTpPrices);
        this.trailingStopPrice = null;
        this.trailingStopActive = false;
        this.highestPriceSinceEntry = entry;
    }

    /**
     * Mark a TP level as hit.
     */
    public void markTpLevelHit(int level) {
        tpLevelsHit.add(level);
    }

    /**
     * Check if a TP level was already hit.
     */
    public boolean isTpLevelHit(int level) {
        return tpLevelsHit.contains(level);
    }

    /**
     * Update remaining quantity after partial close.
     */
    public void reduceQuantity(BigDecimal closedQuantity) {
        if (remainingQuantity != null) {
            remainingQuantity = remainingQuantity.subtract(closedQuantity);

            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                resetPosition();
            }
        }
    }

    /**
     * Update highest price for trailing stop.
     */
    public void updateHighestPrice(BigDecimal currentPrice) {
        if (highestPriceSinceEntry == null ||
            currentPrice.compareTo(highestPriceSinceEntry) > 0) {
            highestPriceSinceEntry = currentPrice;
        }
    }

    /**
     * Update dynamic TP price for a specific level.
     */
    public void updateDynamicTpPrice(int level, BigDecimal newPrice) {
        if (level >= 0 && level < dynamicTpPrices.size()) {
            dynamicTpPrices.set(level, newPrice);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "TrendFollowingState[position=%s, entry=%s, remaining=%s, tpHit=%s, trailing=%s(%s)]",
            currentPositionId != null ? currentPositionId.substring(0, 8) : "none",
            entryPrice,
            remainingQuantity,
            tpLevelsHit,
            trailingStopActive,
            trailingStopPrice
        );
    }
}
