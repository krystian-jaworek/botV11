package com.tradingbot.backtest.permutation;

import com.tradingbot.core.algorithms.AlgorithmConfig;
import com.tradingbot.core.metrics.SimulationResult;
import com.tradingbot.core.models.Candle;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Represents a single simulation task for parallel execution.
 *
 * NOTE: Does NOT store algorithm instances to avoid memory leaks.
 * Algorithms are created on-demand in executeTask().
 */
@Value
@Builder
public class SimulationTask {
    int taskId;
    String tradingPair;
    AlgorithmConfig algorithmConfig;  // Changed from TradingAlgorithm to AlgorithmConfig
    Class<?> algorithmClass;  // Class to instantiate (e.g., GridBotAlgorithm.class)
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
