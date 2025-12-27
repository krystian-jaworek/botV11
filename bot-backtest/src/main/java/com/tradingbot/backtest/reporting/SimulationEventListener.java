package com.tradingbot.backtest.reporting;

import com.tradingbot.core.metrics.SimulationResult;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.ClosedPosition;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.core.models.Position;

import java.math.BigDecimal;

/**
 * Interface for listening to simulation events.
 * Implementations can log, report, or analyze simulation progress.
 */
public interface SimulationEventListener {

    /**
     * Called when simulation starts
     */
    void onSimulationStarted(String algorithmName, String tradingPair, BigDecimal initialBalance);

    /**
     * Called when a position is opened
     */
    void onPositionOpened(Position position, Candle candle, Portfolio portfolio);

    /**
     * Called when a position is closed
     */
    void onPositionClosed(ClosedPosition position, Candle candle, Portfolio portfolio);

    /**
     * Called when simulation is interrupted
     */
    void onSimulationInterrupted(String reason);

    /**
     * Called when simulation completes
     */
    void onSimulationCompleted(SimulationResult result);
}
