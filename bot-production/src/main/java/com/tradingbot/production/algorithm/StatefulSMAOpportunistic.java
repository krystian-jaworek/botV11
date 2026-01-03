package com.tradingbot.production.algorithm;

import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticAlgorithm;
import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticConfig;
import com.tradingbot.core.algorithms.AlgorithmState;
import com.tradingbot.core.algorithms.TradingDecision;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.ClosedPosition;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.core.models.Position;
import com.tradingbot.production.model.AlgorithmType;
import lombok.extern.slf4j.Slf4j;

/**
 * Stateful wrapper for SMAOpportunisticAlgorithm.
 * Delegates to SMAOpportunisticAlgorithm for trading logic.
 */
@Slf4j
public class StatefulSMAOpportunistic implements StatefulAlgorithm {

    private final SMAOpportunisticAlgorithm delegate;
    private final SMAOpportunisticConfig config;

    public StatefulSMAOpportunistic(SMAOpportunisticConfig config) {
        this.config = config;
        this.delegate = new SMAOpportunisticAlgorithm(config);
    }

    @Override
    public String getName() {
        return "SMA Opportunistic";
    }

    @Override
    public SMAOpportunisticConfig getConfig() {
        return config;
    }

    @Override
    public TradingDecision onCandle(Candle candle, Portfolio portfolio) {
        return delegate.onCandle(candle, portfolio);
    }

    @Override
    public AlgorithmState getState() {
        // Delegate to SMAOpportunisticAlgorithm's state
        return delegate.getState();
    }

    @Override
    public void restoreState(AlgorithmState state) {
        // Delegate to SMAOpportunisticAlgorithm's restore
        delegate.restoreState(state);
    }

    @Override
    public void onPositionOpened(Position position) {
        delegate.onPositionOpened(position);
    }

    @Override
    public void onPositionClosed(ClosedPosition closedPosition) {
        delegate.onPositionClosed(closedPosition);
    }

    @Override
    public com.tradingbot.production.model.AlgorithmState captureProductionState() {
        // Get algorithm state from delegate
        AlgorithmState coreState = delegate.getState();

        // Convert to production AlgorithmState
        // Note: This is a simplified version. Full implementation would need to extract
        // all state fields from SMAOpportunisticAlgorithm
        return com.tradingbot.production.model.AlgorithmState.builder()
            .build();
    }

    @Override
    public void restoreProductionState(com.tradingbot.production.model.AlgorithmState state) {
        // Restore algorithm state
        // This is called when restarting an algorithm instance
        log.warn("State restoration not fully implemented for StatefulSMAOpportunistic");

        // TODO: Convert production AlgorithmState back to core AlgorithmState
        // and restore it to the delegate
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.SMA_OPPORTUNISTIC;
    }
}
