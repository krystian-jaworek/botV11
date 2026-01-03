package com.tradingbot.production.service;

import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticConfig;
import com.tradingbot.core.algorithms.TradingDecision;
import com.tradingbot.core.models.*;
import com.tradingbot.production.algorithm.StatefulAlgorithm;
import com.tradingbot.production.algorithm.StatefulSMAOpportunistic;
import com.tradingbot.production.exchange.ByBitApiException;
import com.tradingbot.production.exchange.ByBitFuturesClient;
import com.tradingbot.production.model.*;
import com.tradingbot.production.repository.AlgorithmInstanceRepository;
import com.tradingbot.production.repository.ExecutionLogRepository;
import com.tradingbot.production.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Service for executing algorithm iterations in production.
 * Called by scheduler every minute to process latest candle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlgorithmRuntimeService {

    private final AlgorithmInstanceRepository instanceRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final ByBitFuturesClient bybitClient;
    private final EncryptionService encryptionService;

    /**
     * Execute one iteration for an algorithm instance.
     * This is called by the scheduler every minute.
     *
     * @param instanceId Algorithm instance ID
     */
    @Transactional
    public void executeIteration(String instanceId) {
        long startTime = System.currentTimeMillis();

        AlgorithmInstanceDocument instance = instanceRepository.findById(instanceId)
            .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + instanceId));

        try {
            // 1. Fetch latest candle from ByBit
            String apiKey = instance.getBybitApiKey();
            String apiSecret = encryptionService.decrypt(instance.getBybitApiSecretEncrypted());

            Candle candle = bybitClient.getLastCompletedCandle(
                instance.getTradingPair(),
                apiKey,
                apiSecret
            );

            // 2. Check if we already processed this candle
            if (instance.getLastCandleTimestamp() != null &&
                candle.timestamp() <= instance.getLastCandleTimestamp()) {
                log.debug("Candle already processed for instance {}, skipping", instanceId);
                return;
            }

            // 3. Create algorithm and restore state
            StatefulAlgorithm algorithm = createAlgorithm(instance);
            algorithm.restoreState(instance.getState());

            // 4. Build current portfolio
            Portfolio portfolio = buildPortfolio(instance.getState(), candle.close());

            // 5. Backup state before execution
            AlgorithmState stateBefore = AlgorithmState.builder()
                .cashBalance(instance.getState().getCashBalance())
                .currentPosition(instance.getState().getCurrentPosition())
                .build();

            // 6. Execute algorithm
            TradingDecision decision = algorithm.onCandle(candle, portfolio);

            log.info("[{}] Decision: {}", instance.getName(), decision.getClass().getSimpleName());

            // 7. Execute trading decision
            LiveFilledOrder filledOrder = null;
            if (!(decision instanceof TradingDecision.Hold)) {
                filledOrder = executeDecision(decision, instance, candle.close(), apiKey, apiSecret);
            }

            // 8. Capture new state
            AlgorithmState newState = algorithm.captureState();

            // Update state with portfolio info
            newState.setCashBalance(portfolio.getCashBalance());
            newState.setCurrentPosition(portfolio.hasOpenPosition() ? portfolio.getOpenPosition() : null);

            // 9. Update and save instance
            instance.setState(newState);
            instance.setLastCandleTimestamp(candle.timestamp());
            instance.setLastExecutedAt(Instant.now());
            instance.resetErrors();
            instanceRepository.save(instance);

            // 10. Log execution
            long executionTime = System.currentTimeMillis() - startTime;
            logExecution(instanceId, candle, decision, stateBefore, filledOrder, null, executionTime);

            log.info("[{}] Iteration completed in {}ms", instance.getName(), executionTime);

        } catch (Exception e) {
            handleError(instance, e, startTime);
        }
    }

    /**
     * Execute trading decision (open/increase/close position)
     */
    private LiveFilledOrder executeDecision(
        TradingDecision decision,
        AlgorithmInstanceDocument instance,
        BigDecimal currentPrice,
        String apiKey,
        String apiSecret
    ) throws ByBitApiException {

        TradingPair pair = instance.getTradingPair();

        if (decision instanceof TradingDecision.OpenPosition open) {
            // Calculate quantity from value
            BigDecimal quantity = open.value().divide(currentPrice, pair.getQuantityPrecision(), java.math.RoundingMode.DOWN);

            LiveFilledOrder order = bybitClient.placeMarketOrder(pair, OrderSide.BUY, quantity, apiKey, apiSecret);
            log.info("[{}] Position opened: {} {} at ~{}", instance.getName(), quantity, pair.getSymbol(), currentPrice);

            return order;

        } else if (decision instanceof TradingDecision.IncreasePosition increase) {
            BigDecimal quantity = increase.value().divide(currentPrice, pair.getQuantityPrecision(), java.math.RoundingMode.DOWN);

            LiveFilledOrder order = bybitClient.placeMarketOrder(pair, OrderSide.BUY, quantity, apiKey, apiSecret);
            log.info("[{}] Position increased: {} {} at ~{}", instance.getName(), quantity, pair.getSymbol(), currentPrice);

            return order;

        } else if (decision instanceof TradingDecision.ClosePosition close) {
            Position position = instance.getState().getCurrentPosition();
            if (position == null) {
                log.warn("[{}] No position to close", instance.getName());
                return null;
            }

            LiveFilledOrder order = bybitClient.placeMarketOrder(pair, OrderSide.SELL, position.getQuantity(), apiKey, apiSecret);
            log.info("[{}] Position closed: {} {} at ~{}", instance.getName(), position.getQuantity(), pair.getSymbol(), currentPrice);

            return order;
        }

        return null;
    }

    /**
     * Build portfolio from saved state
     */
    private Portfolio buildPortfolio(AlgorithmState state, BigDecimal currentPrice) {
        Portfolio portfolio = new Portfolio(state.getInitialBalance());

        // Set current cash balance (different from initial if trades happened)
        portfolio.setCashBalance(state.getCashBalance());

        Position position = state.getCurrentPosition();
        if (position != null) {
            // Manually add position without deducting cash (already deducted in past)
            // We need to restore portfolio to exact state
            portfolio.getOpenPositions().put(position.getId(), position);
        }

        return portfolio;
    }

    /**
     * Create algorithm instance based on type and config
     */
    private StatefulAlgorithm createAlgorithm(AlgorithmInstanceDocument instance) {
        return switch (instance.getType()) {
            case SMA_OPPORTUNISTIC -> {
                StatefulSMAOpportunistic algo = new StatefulSMAOpportunistic(instance.getConfig());
                algo.setInitialBalance(instance.getState().getInitialBalance());
                yield algo;
            }
            // Future algorithm types can be added here
        };
    }

    /**
     * Handle execution error
     */
    private void handleError(AlgorithmInstanceDocument instance, Exception error, long startTime) {
        log.error("[{}] Execution error: {}", instance.getName(), error.getMessage(), error);

        instance.recordError(error.getMessage());

        // Stop algorithm if too many errors
        if (instance.shouldStopDueToErrors()) {
            instance.setStatus(AlgorithmStatus.ERROR);
            log.error("[{}] Algorithm stopped due to {} consecutive errors", instance.getName(), instance.getConsecutiveErrors());
        }

        instanceRepository.save(instance);

        // Log error
        long executionTime = System.currentTimeMillis() - startTime;
        logExecution(instance.getId(), null, null, null, null, error.getMessage(), executionTime);
    }

    /**
     * Log execution to MongoDB
     */
    private void logExecution(
        String instanceId,
        Candle candle,
        TradingDecision decision,
        AlgorithmState stateBefore,
        LiveFilledOrder filledOrder,
        String error,
        long executionTimeMs
    ) {
        ExecutionLogDocument log = ExecutionLogDocument.builder()
            .instanceId(instanceId)
            .timestamp(Instant.now())
            .candle(candle)
            .decision(decision)
            .stateBefore(stateBefore)
            .filledOrder(filledOrder)
            .error(error)
            .executionTimeMs(executionTimeMs)
            .build();

        executionLogRepository.save(log);
    }
}
