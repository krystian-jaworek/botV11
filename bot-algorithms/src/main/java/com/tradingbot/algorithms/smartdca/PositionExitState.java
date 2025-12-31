package com.tradingbot.algorithms.smartdca;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks exit-related state for a position.
 * This is separate from Position because Position is immutable and represents
 * the current state at a point in time, while this tracks dynamic exit strategy state.
 */
public class PositionExitState {

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

    public PositionExitState() {
        this.peakPrice = BigDecimal.ZERO;
        this.breakevenStopActive = false;
        this.trailingStopActive = false;
        this.triggeredStages = new ArrayList<>();
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
     * Reset all state (call when position is fully closed)
     */
    public void reset() {
        this.peakPrice = BigDecimal.ZERO;
        this.breakevenStopActive = false;
        this.trailingStopActive = false;
        this.triggeredStages.clear();
    }
}
