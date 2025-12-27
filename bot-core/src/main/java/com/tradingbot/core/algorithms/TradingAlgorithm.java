package com.tradingbot.core.algorithms;

import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.ClosedPosition;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.core.models.Position;

import java.math.BigDecimal;

/**
 * Main interface for all trading algorithms.
 * Implementations must be stateful and thread-safe if used in concurrent contexts.
 *
 * @param <C> Configuration type for this algorithm
 */
public interface TradingAlgorithm<C extends AlgorithmConfig> {

    /**
     * Get the algorithm name (e.g., "GridBot", "MomentumStrategy")
     */
    String getName();

    /**
     * Get the algorithm's configuration
     */
    C getConfig();

    /**
     * Main decision method - called on each new candle.
     * The algorithm should analyze the candle and portfolio state,
     * then return a trading decision.
     *
     * @param candle Current market candle
     * @param portfolio Current portfolio state
     * @return Trading decision (OpenPosition, ClosePosition, or Hold)
     */
    TradingDecision onCandle(Candle candle, Portfolio portfolio);

    /**
     * Get current internal state of the algorithm.
     * Used for persistence and recovery.
     *
     * @return Serializable state object
     */
    AlgorithmState getState();

    /**
     * Restore algorithm from a previous state.
     * Used for recovery after restart.
     *
     * @param state Previously saved state
     */
    void restoreState(AlgorithmState state);

    /**
     * Initialize the algorithm.
     * Called once before first candle.
     *
     * @param initialPrice Starting price for grid calculation, etc.
     */
    default void initialize(BigDecimal initialPrice) {
        // Optional initialization hook
    }

    /**
     * Callback invoked after a position is successfully opened.
     * Algorithms can override this to track positions internally.
     *
     * @param position The opened position
     */
    default void onPositionOpened(Position position) {
        // Optional callback hook
    }

    /**
     * Callback invoked after a position is successfully closed.
     * Algorithms can override this to update internal state.
     *
     * @param closedPosition The closed position
     */
    default void onPositionClosed(ClosedPosition closedPosition) {
        // Optional callback hook
    }
}
