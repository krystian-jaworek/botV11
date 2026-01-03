package com.tradingbot.production.scheduler;

import com.tradingbot.core.algorithms.TradingDecision;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.ClosedPosition;
import com.tradingbot.core.models.Position;
import com.tradingbot.production.exchange.BybitMarketDataService;
import com.tradingbot.production.exchange.BybitOrderExecutor;
import com.tradingbot.production.service.AlgorithmOrchestrator;
import com.tradingbot.production.state.AlgorithmInstance;
import com.tradingbot.production.state.AlgorithmStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Scheduled task that triggers trading algorithms every minute.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trading.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class TradingScheduler {

    private final AlgorithmOrchestrator orchestrator;
    private final AlgorithmStateManager stateManager;
    private final BybitMarketDataService marketDataService;
    private final BybitOrderExecutor orderExecutor;

    /**
     * Execute every minute
     */
    @Scheduled(fixedDelayString = "${trading.scheduler.interval-ms:60000}")
    public void executeTradingCycle() {
        log.debug("Trading cycle started");

        List<AlgorithmInstance> activeInstances = orchestrator.getAllActiveInstances();

        if (activeInstances.isEmpty()) {
            log.debug("No active algorithms");
            return;
        }

        log.info("Processing {} active algorithms", activeInstances.size());

        for (AlgorithmInstance instance : activeInstances) {
            try {
                processAlgorithm(instance);
            } catch (Exception e) {
                log.error("Error processing algorithm: {}", instance.getAlgorithmId(), e);
            }
        }

        log.debug("Trading cycle completed");
    }

    /**
     * Process a single algorithm instance
     */
    private void processAlgorithm(AlgorithmInstance instance) {
        try {
            // Get current market data
            Candle currentCandle = marketDataService.getCurrentCandle(instance.getTradingPair());

            if (currentCandle == null) {
                log.warn("No candle data for {}", instance.getTradingPair());
                return;
            }

            // Get algorithm decision
            TradingDecision decision = instance.getAlgorithm().onCandle(
                currentCandle,
                instance.getPortfolio()
            );

            // Execute decision
            executeDecision(instance, decision, currentCandle);

            // Update instance timestamp
            instance.setLastUpdateAt(java.time.Instant.now());

            // Save state to MongoDB
            stateManager.saveState(instance);

            log.debug("Processed algorithm: {} - Decision: {}",
                instance.getAlgorithmId(),
                decision.getClass().getSimpleName());

        } catch (Exception e) {
            log.error("Failed to process algorithm: {}", instance.getAlgorithmId(), e);
        }
    }

    /**
     * Execute trading decision
     */
    private void executeDecision(AlgorithmInstance instance, TradingDecision decision, Candle candle) {
        switch (decision) {
            case TradingDecision.OpenPosition open -> {
                Position position = orderExecutor.openPosition(
                    instance.getTradingPair(),
                    open.side(),
                    open.quantity(),
                    open.price(),
                    candle.timestamp(),
                    open.metadata()
                );

                // Notify algorithm
                instance.getAlgorithm().onPositionOpened(position);

                log.info("[{}] Opened position: {} at {}",
                    instance.getAlgorithmId(),
                    position.getId().substring(0, 8),
                    position.getEntryPrice());
            }
            case TradingDecision.ClosePosition close -> {
                Position position = instance.getPortfolio()
                    .getOpenPositions()
                    .get(close.positionId());

                if (position != null) {
                    ClosedPosition closedPosition = orderExecutor.closePosition(
                        instance.getTradingPair(),
                        position,
                        close.price(),
                        candle.timestamp()
                    );

                    // Notify algorithm
                    instance.getAlgorithm().onPositionClosed(closedPosition);

                    log.info("[{}] Closed position: {} PnL: {} ({}%)",
                        instance.getAlgorithmId(),
                        closedPosition.getId().substring(0, 8),
                        closedPosition.getRealizedPnL(),
                        closedPosition.getRealizedPnLPercentage());
                } else {
                    log.warn("Position not found: {}", close.positionId());
                }
            }
            case TradingDecision.Hold hold -> {
                // No action
            }
            default -> {
                // Handle other cases (IncreasePosition, ClosePositionPartial, etc.)
                log.warn("Unhandled trading decision type: {}", decision.getClass().getSimpleName());
            }
        }
    }
}
