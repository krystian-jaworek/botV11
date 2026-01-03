package com.tradingbot.production.algorithm;

import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticAlgorithm;
import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticConfig;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.core.models.Position;
import com.tradingbot.core.models.TradingDecision;
import com.tradingbot.production.model.AlgorithmState;
import com.tradingbot.production.model.AlgorithmType;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * Stateful wrapper for SMAOpportunisticAlgorithm.
 * Delegates trading logic to SMAOpportunisticAlgorithm while adding state management.
 */
@Slf4j
public class StatefulSMAOpportunistic implements StatefulAlgorithm {

    private final SMAOpportunisticAlgorithm delegate;
    private final SMAOpportunisticConfig config;

    // State tracking
    private BigDecimal initialBalance;
    private BigDecimal maxEquity;
    private BigDecimal minCashBalance;
    private BigDecimal maxPositionDrawdown;
    private int totalTrades;

    public StatefulSMAOpportunistic(SMAOpportunisticConfig config) {
        this.config = config;
        this.delegate = new SMAOpportunisticAlgorithm(config);
        this.initialBalance = BigDecimal.ZERO;
        this.maxEquity = BigDecimal.ZERO;
        this.minCashBalance = BigDecimal.ZERO;
        this.maxPositionDrawdown = BigDecimal.ZERO;
        this.totalTrades = 0;
    }

    @Override
    public TradingDecision onCandle(Candle candle, Portfolio portfolio) {
        // Delegate to SMAOpportunistic algorithm
        TradingDecision decision = delegate.onCandle(candle, portfolio);

        // Update metrics
        updateMetrics(portfolio, candle.close());

        // Track trades
        if (!(decision instanceof TradingDecision.Hold)) {
            totalTrades++;
        }

        return decision;
    }

    @Override
    public void onPositionOpened(Position position) {
        delegate.onPositionOpened(position);
    }

    @Override
    public void onPositionIncreased(Position position) {
        delegate.onPositionIncreased(position);
    }

    @Override
    public void onPositionClosed() {
        delegate.onPositionClosed();
    }

    @Override
    public String getAlgorithmName() {
        return delegate.getAlgorithmName();
    }

    @Override
    public AlgorithmState captureState() {
        // Access internal state through reflection or add getters to SMAOpportunisticAlgorithm
        // For now, we'll capture what we can track here

        return AlgorithmState.builder()
            .cashBalance(BigDecimal.ZERO)  // Will be set from portfolio in RuntimeService
            .initialBalance(initialBalance)
            .currentPosition(null)  // Will be set from portfolio in RuntimeService
            .lastBuyTimestamp(null)  // TODO: Need to expose from SMAOpportunisticAlgorithm
            .lastBuyPrice(null)  // TODO: Need to expose from SMAOpportunisticAlgorithm
            .candleClosePrices(new ArrayList<>())  // TODO: Need to expose from SMAOpportunisticAlgorithm
            .smaSum(BigDecimal.ZERO)  // TODO: Need to expose from SMAOpportunisticAlgorithm
            .smaCount(0)  // TODO: Need to expose from SMAOpportunisticAlgorithm
            .maxEquity(maxEquity)
            .minCashBalance(minCashBalance)
            .maxPositionDrawdownPercentage(maxPositionDrawdown)
            .totalTrades(totalTrades)
            .build();
    }

    @Override
    public void restoreState(AlgorithmState state) {
        if (state == null) {
            return;
        }

        this.initialBalance = state.getInitialBalance();
        this.maxEquity = state.getMaxEquity() != null ? state.getMaxEquity() : state.getInitialBalance();
        this.minCashBalance = state.getMinCashBalance() != null ? state.getMinCashBalance() : state.getInitialBalance();
        this.maxPositionDrawdown = state.getMaxPositionDrawdownPercentage() != null ? state.getMaxPositionDrawdownPercentage() : BigDecimal.ZERO;
        this.totalTrades = state.getTotalTrades() != null ? state.getTotalTrades() : 0;

        // TODO: Restore internal state to SMAOpportunisticAlgorithm
        // This requires exposing setState() method in SMAOpportunisticAlgorithm
        log.warn("State restoration partially implemented - internal algorithm state not fully restored");
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.SMA_OPPORTUNISTIC;
    }

    /**
     * Update performance metrics
     */
    private void updateMetrics(Portfolio portfolio, BigDecimal currentPrice) {
        BigDecimal currentEquity = portfolio.getTotalEquity(currentPrice);
        BigDecimal currentCash = portfolio.getCashBalance();

        // Update max/min tracking
        if (currentEquity.compareTo(maxEquity) > 0) {
            maxEquity = currentEquity;
        }

        if (minCashBalance.compareTo(BigDecimal.ZERO) == 0 || currentCash.compareTo(minCashBalance) < 0) {
            minCashBalance = currentCash;
        }

        // Track position drawdown
        if (portfolio.hasOpenPosition()) {
            Position position = portfolio.getOpenPosition();
            BigDecimal entryValue = position.getAverageEntryPrice().multiply(position.getQuantity());
            BigDecimal currentValue = currentPrice.multiply(position.getQuantity());
            BigDecimal drawdown = entryValue.subtract(currentValue)
                .divide(entryValue, 6, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

            if (drawdown.compareTo(maxPositionDrawdown) > 0) {
                maxPositionDrawdown = drawdown;
            }
        }
    }

    /**
     * Set initial balance (called when creating new instance)
     */
    public void setInitialBalance(BigDecimal balance) {
        this.initialBalance = balance;
        this.maxEquity = balance;
        this.minCashBalance = balance;
    }
}
