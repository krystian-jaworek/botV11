package com.tradingbot.backtest.permutation;

import com.tradingbot.core.algorithms.TradingAlgorithm;
import com.tradingbot.core.metrics.SimulationResult;
import com.tradingbot.core.models.Candle;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Represents a single simulation task for parallel execution.
 */
@Value
@Builder
public class SimulationTask {
    int taskId;
    String tradingPair;
    TradingAlgorithm<?> algorithm;
    List<Candle> candles;
    BigDecimal initialBalance;

    /**
     * Result container for completed simulation
     */
    @Value
    @Builder
    public static class Result {
        int taskId;
        SimulationResult simulationResult;
        long executionTimeMillis;
        boolean success;
        String errorMessage;
        Object algorithmConfig;  // Algorithm-specific configuration (e.g., GridBotConfig)

        public static Result success(int taskId, SimulationResult result, Object config, long executionTime) {
            return Result.builder()
                .taskId(taskId)
                .simulationResult(result)
                .algorithmConfig(config)
                .executionTimeMillis(executionTime)
                .success(true)
                .build();
        }

        public static Result failure(int taskId, String errorMessage, long executionTime) {
            return Result.builder()
                .taskId(taskId)
                .executionTimeMillis(executionTime)
                .success(false)
                .errorMessage(errorMessage)
                .build();
        }
    }
}
