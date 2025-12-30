package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.smartdca.SmartDCAConfig;
import com.tradingbot.algorithms.smartdca.SmartOpportunisticDCAAlgorithm;
import com.tradingbot.backtest.data.CandleFileReader;
import com.tradingbot.backtest.permutation.*;
import com.tradingbot.backtest.reporting.ConsoleReporter;
import com.tradingbot.core.models.Candle;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Smart DCA permutation runner for parallel parameter sweep simulations.
 *
 * Runs Smart Opportunistic DCA simulations with different parameter combinations using Virtual Threads.
 *
 * Usage: [candle-file] [permutation-mode]
 *
 * Examples:
 *   (no arguments) → Uses default: BTCUSDT-1-365.txt, compact mode
 *   ETHUSDT → Uses ETHUSDT-1-365.txt, compact mode
 *   BTCUSDT full → Uses BTCUSDT-1-365.txt, full permutation mode
 *
 * Permutation modes:
 * - compact: 1 configuration (quick test with defaults)
 * - default: ~81 configurations (3x3x3x3 parameters)
 *
 * Parameter ranges (default mode):
 * - RSI oversold: 35, 40, 45 = 3 values
 * - Min price drop: 3%, 4%, 5% = 3 values
 * - Volume spike: 1.2x, 1.4x, 1.6x = 3 values
 * - Tier 1 size: 3%, 4%, 5% = 3 values
 * Total: 3 * 3 * 3 * 3 = 81 simulations
 */
@Slf4j
public class SmartDCAPermutationRunner {

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000");
    private static final String DEFAULT_CANDLE_FILE = "BTCUSDT-1-365.txt";

    public static void main(String[] args) {
        String candleFileName = resolveCandleFileName(args);

        // Determine permutation mode
        String mode = "compact";
        if (args.length > 1) {
            mode = args[1].toLowerCase();
        }

        try {
            runPermutations(candleFileName, mode);
        } catch (Exception e) {
            log.error("Permutation run failed", e);
            System.err.println("Permutation run failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String resolveCandleFileName(String[] args) {
        if (args.length == 0) {
            log.info("No candle file provided, using default: {}", DEFAULT_CANDLE_FILE);
            return DEFAULT_CANDLE_FILE;
        }

        String input = args[0];
        if (input.endsWith(".txt")) {
            return input;
        }

        String fileName = input + "-1-365.txt";
        log.info("Trading pair provided, using: {}", fileName);
        return fileName;
    }

    private static void runPermutations(String candleFileName, String mode) throws Exception {
        log.info("Starting Smart DCA permutation run");
        log.info("Candle file: {}", candleFileName);
        log.info("Permutation mode: {}", mode);

        // Parse trading pair
        CandleFileReader.CandleFileMetadata metadata = CandleFileReader.parseFileName(candleFileName);
        String tradingPair = metadata.pair();
        log.info("Trading pair: {}", tradingPair);

        // Read candles
        CandleFileReader reader = new CandleFileReader();
        List<Candle> candles = reader.readCandlesFromClasspath(candleFileName);
        log.info("Loaded {} candles", candles.size());

        // Generate parameter permutations
        SmartDCAParameterPermutation permutation = mode.equals("full")
            ? SmartDCAParameterPermutation.defaultPermutation()
            : SmartDCAParameterPermutation.compactPermutation();

        List<SmartDCAConfig> configurations = permutation.generateConfigurations();
        log.info("Generated {} configurations", configurations.size());

        // Create simulation tasks
        List<SimulationTask> tasks = new ArrayList<>();
        for (int i = 0; i < configurations.size(); i++) {
            SmartDCAConfig config = configurations.get(i);
            SmartOpportunisticDCAAlgorithm algorithm = new SmartOpportunisticDCAAlgorithm(config);

            SimulationTask task = SimulationTask.builder()
                .taskId(i)
                .tradingPair(tradingPair)
                .algorithm(algorithm)
                .candles(candles)
                .initialBalance(INITIAL_CAPITAL)
                .build();

            tasks.add(task);
        }

        // Create parallel executor (no persistence for now)
        ParallelSimulationExecutor executor = new ParallelSimulationExecutor(
            result -> {
                // No-op callback (no persistence)
            },
            configurations.size() > 10 ? 10 : 1  // Report every 10 simulations if > 10 total
        );

        // Execute all simulations
        log.info("Starting parallel execution with Virtual Threads...");
        List<SimulationTask.Result> results = executor.executeAll(tasks);

        // DEBUG: Log top results from memory
        log.info("DEBUG: Top 5 results from memory:");
        results.stream()
            .filter(SimulationTask.Result::isSuccess)
            .filter(r -> !r.getSimulationResult().isInterrupted())
            .sorted((a, b) -> b.getSimulationResult().getProfitPercentage()
                .compareTo(a.getSimulationResult().getProfitPercentage()))
            .limit(5)
            .forEach(r -> log.info("  Profit: {}%, Success: {}, Interrupted: {}, TaskId: {}",
                r.getSimulationResult().getProfitPercentage(),
                r.isSuccess(),
                r.getSimulationResult().isInterrupted(),
                r.getTaskId()));

        // Aggregate and display results
        log.info("Aggregating results...");
        ResultsAggregator.Summary summary = ResultsAggregator.aggregate(results);

        System.out.println();
        System.out.println(summary);
        System.out.println();

        // Display top 10 configurations with algorithm config
        System.out.println("===== TOP 10 CONFIGURATIONS =====");

        // Get top 10 results with their configs
        List<SimulationTask.Result> topTenWithConfigs = results.stream()
            .filter(SimulationTask.Result::isSuccess)
            .filter(r -> !r.getSimulationResult().isInterrupted())
            .sorted((a, b) -> b.getSimulationResult().getProfitPercentage()
                .compareTo(a.getSimulationResult().getProfitPercentage()))
            .limit(10)
            .collect(java.util.stream.Collectors.toList());

        for (int i = 0; i < topTenWithConfigs.size(); i++) {
            var taskResult = topTenWithConfigs.get(i);
            var result = taskResult.getSimulationResult();
            SmartDCAConfig config = (SmartDCAConfig) taskResult.getAlgorithmConfig();

            System.out.println();
            System.out.printf("Rank #%d%n", i + 1);
            System.out.println("-".repeat(80));
            System.out.printf("Algorithm Config: %s%n", config.getConfigId());
            System.out.printf("  RSI Oversold Threshold: %d%n",
                config.getBuyConditions().getRsi().getOversoldThreshold());
            System.out.printf("  Min Price Drop: %.1f%%%n",
                config.getBuyConditions().getPriceDrop().getMinFromLastBuyPct());
            System.out.printf("  Tier 1 Size: %.1f%%%n",
                config.getPositionSizing().getTiers().get(0).getSizePct());
            System.out.println();
            System.out.printf("Profit: %.2f%% | Max DD: %.2f%% | Trades: %d%n",
                result.getProfitPercentage(),
                result.getMaxPortfolioDrawdownPercentage(),
                result.getTotalTradesExecuted());

            System.out.println("-".repeat(80));
        }

        // Display filled orders table for best result
        if (!topTenWithConfigs.isEmpty()) {
            System.out.println();
            System.out.println("===== BEST RESULT FILLED ORDERS =====");
            var bestResult = topTenWithConfigs.get(0).getSimulationResult();
            if (bestResult.getFilledOrders() != null && !bestResult.getFilledOrders().isEmpty()) {
                ConsoleReporter.printFilledOrdersTable(bestResult.getFilledOrders());
            } else {
                System.out.println("No filled orders available for best result");
            }
        }

        log.info("Permutation run completed");
    }
}
