package com.tradingbot.production.algorithm;

import com.tradingbot.core.algorithms.TradingAlgorithm;
import com.tradingbot.production.model.AlgorithmState;
import com.tradingbot.production.model.AlgorithmType;

/**
 * Extension of TradingAlgorithm that supports production state capture and restoration.
 * Required for production trading where algorithm state must be persisted after each candle.
 */
public interface StatefulAlgorithm extends TradingAlgorithm {

    /**
     * Capture current algorithm state after processing a candle.
     * This state will be persisted to MongoDB.
     *
     * @return Current algorithm state (production model)
     */
    AlgorithmState captureProductionState();

    /**
     * Restore algorithm from previously saved production state.
     * Called when restarting an algorithm instance.
     *
     * @param state Previously saved state (production model)
     */
    void restoreProductionState(AlgorithmState state);

    /**
     * Get the type of this algorithm
     *
     * @return Algorithm type
     */
    AlgorithmType getType();
}
