package com.tradingbot.backtest.engine;

import com.tradingbot.backtest.data.BacktestMarketDataProvider;
import com.tradingbot.backtest.executor.MockOrderExecutor;
import com.tradingbot.backtest.reporting.SimulationEventListener;
import com.tradingbot.core.algorithms.OrderExecutor;
import com.tradingbot.core.algorithms.TradingAlgorithm;
import com.tradingbot.core.algorithms.TradingDecision;
import com.tradingbot.core.metrics.MetricsCalculator;
import com.tradingbot.core.metrics.SimulationResult;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.ClosedPosition;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.core.models.Position;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Main backtesting engine.
 * Runs a trading algorithm on historical data and produces metrics.
 */
@Slf4j
public class BacktestEngine {

    private final String tradingPair;
    private final BigDecimal initialBalance;
    private final List<SimulationEventListener> eventListeners;

    public BacktestEngine(String tradingPair, BigDecimal initialBalance) {
        this.tradingPair = tradingPair;
        this.initialBalance = initialBalance;
        this.eventListeners = new ArrayList<>();
    }

    /**
     * Add an event listener for simulation events
     */
    public void addEventListener(SimulationEventListener listener) {
        this.eventListeners.add(listener);
    }

    /**
     * Run a backtest simulation
     *
     * @param algorithm Trading algorithm to test
     * @param candles Historical candle data
     * @return Simulation results with metrics
     */
    public SimulationResult runSimulation(TradingAlgorithm<?> algorithm, List<Candle> candles) {
        log.debug("Starting backtest for {} on {} with {} candles",
            algorithm.getName(), tradingPair, candles.size());

        // Initialize components
        Portfolio portfolio = new Portfolio(initialBalance);
        OrderExecutor orderExecutor = new MockOrderExecutor(portfolio);
        BacktestMarketDataProvider dataProvider = new BacktestMarketDataProvider(candles);
        SimulationInterruptor interruptor = new SimulationInterruptor(initialBalance);

        // Metrics trackers
        MetricsCalculator.PositionDrawdownTracker positionTracker = new MetricsCalculator.PositionDrawdownTracker();
        MetricsCalculator.PortfolioDrawdownTracker portfolioTracker =
            new MetricsCalculator.PortfolioDrawdownTracker(initialBalance);

        // Initialize algorithm with first candle price
        Candle firstCandle = candles.get(0);
        algorithm.initialize(firstCandle.close());

        long startTimestamp = firstCandle.timestamp();
        long endTimestamp = firstCandle.timestamp();

        boolean interrupted = false;
        String interruptionReason = null;

        // Notify start
        notifySimulationStarted(algorithm.getName(), tradingPair, initialBalance);

        // Main simulation loop
        while (dataProvider.hasNext() || dataProvider.getCurrentIndex() == 0) {
            Candle currentCandle = dataProvider.getCurrentCandle();
            BigDecimal currentPrice = currentCandle.close();
            endTimestamp = currentCandle.timestamp();

            // Update equity tracking
            portfolio.updateMaxEquity(currentPrice);
            portfolio.updateMinEquity(currentPrice);
            portfolioTracker.updateEquity(portfolio.getEquity(currentPrice));

            // Update position drawdown tracking
            for (Position position : portfolio.getOpenPositions().values()) {
                positionTracker.updatePosition(position, currentPrice);
            }

            // Check for interruption conditions
            String interruptReason = interruptor.shouldInterrupt(portfolio, currentPrice);
            if (interruptReason != null) {
                interrupted = true;
                interruptionReason = interruptReason;
                notifySimulationInterrupted(interruptionReason);
                break;
            }

            // Get algorithm decision
            TradingDecision decision = algorithm.onCandle(currentCandle, portfolio);

            // Execute decision
            executeDecision(decision, orderExecutor, currentCandle, portfolio, algorithm);

            // Move to next candle
            if (dataProvider.hasNext()) {
                dataProvider.moveNext();
            } else {
                break;
            }
        }

        // Build result
        SimulationResult result = MetricsCalculator.buildResult(
            algorithm.getName(),
            tradingPair,
            startTimestamp,
            endTimestamp,
            initialBalance,
            portfolio,
            candles.get(candles.size() - 1).close(),
            positionTracker,
            portfolioTracker,
            interrupted,
            interruptionReason
        );

        log.debug("Backtest completed. Profit: {} ({}%)",
            result.getProfitAbsolute(), result.getProfitPercentage());

        notifySimulationCompleted(result);

        return result;
    }

    /**
     * Execute a trading decision
     */
    private void executeDecision(TradingDecision decision, OrderExecutor executor,
                                 Candle currentCandle, Portfolio portfolio,
                                 TradingAlgorithm<?> algorithm) {
        switch (decision) {
            case TradingDecision.OpenPosition open -> {
                Position position = executor.openPosition(
                    open.side(),
                    open.quantity(),
                    open.price(),
                    currentCandle.timestamp(),
                    open.metadata()
                );
                algorithm.onPositionOpened(position);
                notifyPositionOpened(position, currentCandle, portfolio);
            }
            case TradingDecision.ClosePosition close -> {
                Position position = portfolio.getOpenPositions().get(close.positionId());
                if (position != null) {
                    ClosedPosition closedPosition = executor.closePosition(
                        position,
                        close.price(),
                        currentCandle.timestamp()
                    );
                    algorithm.onPositionClosed(closedPosition);
                    notifyPositionClosed(closedPosition, currentCandle, portfolio);
                } else {
                    log.warn("Attempted to close non-existent position: {}", close.positionId());
                }
            }
            case TradingDecision.ClosePositionPartial partial -> {
                Position position = portfolio.getOpenPositions().get(partial.positionId());
                if (position != null) {
                    ClosedPosition closedPosition = executor.closePartialPosition(
                        position,
                        partial.quantity(),
                        partial.price(),
                        currentCandle.timestamp()
                    );
                    algorithm.onPositionClosed(closedPosition);
                    notifyPositionClosed(closedPosition, currentCandle, portfolio);
                } else {
                    log.warn("Attempted to partially close non-existent position: {}", partial.positionId());
                }
            }
            case TradingDecision.Hold hold -> {
                // No action needed
            }
        }
    }

    // Event notification methods
    private void notifySimulationStarted(String algorithmName, String pair, BigDecimal initialBalance) {
        for (SimulationEventListener listener : eventListeners) {
            listener.onSimulationStarted(algorithmName, pair, initialBalance);
        }
    }

    private void notifyPositionOpened(Position position, Candle candle, Portfolio portfolio) {
        for (SimulationEventListener listener : eventListeners) {
            listener.onPositionOpened(position, candle, portfolio);
        }
    }

    private void notifyPositionClosed(ClosedPosition position, Candle candle, Portfolio portfolio) {
        for (SimulationEventListener listener : eventListeners) {
            listener.onPositionClosed(position, candle, portfolio);
        }
    }

    private void notifySimulationInterrupted(String reason) {
        for (SimulationEventListener listener : eventListeners) {
            listener.onSimulationInterrupted(reason);
        }
    }

    private void notifySimulationCompleted(SimulationResult result) {
        for (SimulationEventListener listener : eventListeners) {
            listener.onSimulationCompleted(result);
        }
    }
}
