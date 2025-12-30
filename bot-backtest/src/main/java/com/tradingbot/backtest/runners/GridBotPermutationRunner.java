package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.gridbot.GridBotAlgorithm;
import com.tradingbot.algorithms.gridbot.GridBotConfig;
import com.tradingbot.backtest.data.CandleFileReader;
import com.tradingbot.backtest.permutation.*;
import com.tradingbot.backtest.reporting.ConsoleReporter;
import com.tradingbot.core.models.Candle;
import com.tradingbot.persistence.config.MongoConfig;
import com.tradingbot.persistence.repositories.DynamicSimulationResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * GridBot-specific permutation runner for parallel parameter sweep simulations.
 *
 * Runs 300 GridBot simulations with different parameter combinations using Virtual Threads.
 *
 * Usage: [candle-file] [save-to-mongodb]
 *
 * Examples:
 *   (no arguments)
 *     → Uses default: BTCUSDT-1-365.txt, MongoDB enabled (default)
 *
 *   ETHUSDT
 *     → Uses ETHUSDT-1-365.txt, MongoDB enabled (default)
 *
 *   BTCUSDT-5-90.txt
 *     → Uses custom file, MongoDB enabled (default)
 *
 *   BTCUSDT false
 *     → Uses BTCUSDT-1-365.txt, MongoDB disabled
 *
 *   BTCUSDT-1-365.txt false
 *     → Uses custom file, MongoDB disabled
 *
 * Note: MongoDB persistence is ENABLED by default. Use 'false' as second argument to disable.
 *
 * The candle file should be placed in src/main/resources/
 *
 * GridBot parameter ranges (default):
 * - Grid levels: 10-30 (step 5) = 5 values
 * - Grid distance: 0.5-2.0% (step 0.5) = 4 values
 * - Take profit: 1.0-3.0% (step 0.5) = 5 values
 * - Portfolio allocation: 60-80% (step 10) = 3 values
 * Total: 5 * 4 * 5 * 3 = 300 simulations
 */
@Slf4j
public class GridBotPermutationRunner {

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000");
    private static final String DEFAULT_CANDLE_FILE = "BTCUSDT-1-365.txt";

    public static void main(String[] args) {
        String candleFileName = resolveCandleFileName(args);

        // MongoDB persistence enabled by default
        boolean saveToMongo = true;
        if (args.length > 1) {
            saveToMongo = Boolean.parseBoolean(args[1]);
        }

        try {
            runPermutations(candleFileName, saveToMongo);
        } catch (Exception e) {
            log.error("Permutation run failed", e);
            System.err.println("Permutation run failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Resolves the candle file name based on command line arguments.
     *
     * @param args Command line arguments
     * @return The resolved candle file name
     */
    private static String resolveCandleFileName(String[] args) {
        // No arguments → use default
        if (args.length == 0) {
            log.info("No candle file provided, using default: {}", DEFAULT_CANDLE_FILE);
            return DEFAULT_CANDLE_FILE;
        }

        // args[0] contains file or trading pair
        String input = args[0];

        // If it ends with .txt, it's a full filename
        if (input.endsWith(".txt")) {
            return input;
        }

        // Otherwise, it's a trading pair - append -1-365.txt
        String fileName = input + "-1-365.txt";
        log.info("Trading pair provided, using: {}", fileName);
        return fileName;
    }

    private static void runPermutations(String candleFileName, boolean saveToMongo) throws Exception {
        log.info("Starting permutation run");
        log.info("Candle file: {}", candleFileName);
        log.info("Save to MongoDB: {}", saveToMongo);

        // Parse trading pair
        CandleFileReader.CandleFileMetadata metadata = CandleFileReader.parseFileName(candleFileName);
        String tradingPair = metadata.pair();

        log.info("Trading pair: {}", tradingPair);

        // Read candles from classpath (resources)
        CandleFileReader reader = new CandleFileReader();
        List<Candle> candles = reader.readCandlesFromClasspath(candleFileName);

        log.info("Loaded {} candles", candles.size());

        // Generate parameter permutations
        GridBotParameterPermutation permutation = GridBotParameterPermutation.defaultPermutation();
        List<GridBotConfig> configurations = permutation.generateConfigurations();

        log.info("Generated {} configurations", configurations.size());
        log.info("Parameter ranges:");
        log.info("  Grid levels: {}", permutation.getGridLevelsRange());
        log.info("  Grid distance: {}%", permutation.getGridDistanceRange());
        log.info("  Take profit: {}%", permutation.getTakeProfitRange());
        log.info("  Allocation: {}%", permutation.getPortfolioAllocationRange());

        // Create simulation tasks
        List<SimulationTask> tasks = new ArrayList<>();
        for (int i = 0; i < configurations.size(); i++) {
            GridBotConfig config = configurations.get(i);
            GridBotAlgorithm algorithm = new GridBotAlgorithm(config);

            SimulationTask task = SimulationTask.builder()
                .taskId(i)
                .tradingPair(tradingPair)
                .algorithm(algorithm)
                .candles(candles)
                .initialBalance(INITIAL_CAPITAL)
                .build();

            tasks.add(task);
        }

        // Setup MongoDB persistence if requested
        BatchResultsPersister persister = null;
        if (saveToMongo) {
            log.info("Initializing MongoDB connection...");
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.register(MongoConfig.class);
            context.refresh();

            DynamicSimulationResultRepository repository = context.getBean(DynamicSimulationResultRepository.class);
            persister = new BatchResultsPersister(repository);

            log.info("MongoDB persistence enabled (collection: GridBot-{})", tradingPair);
        }

        // Create parallel executor
        BatchResultsPersister finalPersister = persister;
        ParallelSimulationExecutor executor = new ParallelSimulationExecutor(
            result -> {
                if (finalPersister != null) {
                    finalPersister.persist(result);
                }
            },
            50  // Report every 50 simulations
        );

        // Execute all simulations
        log.info("Starting parallel execution with Virtual Threads...");
        List<SimulationTask.Result> results = executor.executeAll(tasks);

        // Final flush if using MongoDB
        if (persister != null) {
            persister.close();
        }

        // DEBUG: Log top results from memory before aggregation
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
            GridBotConfig config = (GridBotConfig) taskResult.getAlgorithmConfig();

            System.out.printf("%d. Profit: %.2f%% | Max DD: %.2f%% | Trades: %d%n",
                i + 1,
                result.getProfitPercentage(),
                result.getMaxPortfolioDrawdownPercentage(),
                result.getTotalTradesExecuted()
            );
            System.out.printf("   Config: levels=%d, distance=%.2f%%, takeProfit=%.2f%%, allocation=%.2f%%%n",
                config.getGridLevels(),
                config.getGridDistancePercent(),
                config.getTakeProfitPercent(),
                config.getPortfolioAllocationPercent()
            );
        }
        System.out.println("=================================");

        // If MongoDB is enabled, show MongoDB IDs for current run's top results
        if (saveToMongo) {
            System.out.println();
            System.out.println("===== TOP 10 WITH MONGODB IDs (CURRENT RUN) =====");

            // Wait a moment to ensure MongoDB flush completed
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.register(MongoConfig.class);
            context.refresh();

            DynamicSimulationResultRepository repository = context.getBean(DynamicSimulationResultRepository.class);

            // For each top result, find its MongoDB ID by matching config
            for (int i = 0; i < topTenWithConfigs.size() && i < 10; i++) {
                var taskResult = topTenWithConfigs.get(i);
                var result = taskResult.getSimulationResult();
                GridBotConfig config = (GridBotConfig) taskResult.getAlgorithmConfig();

                // Try to find this exact config in MongoDB
                List<com.tradingbot.persistence.entities.SimulationResultDocument> allDocs =
                    repository.findAll("GridBot", tradingPair);

                String mongoId = "Not found in DB";
                for (var doc : allDocs) {
                    if (Math.abs(doc.getProfitPercentage().doubleValue() - result.getProfitPercentage().doubleValue()) < 0.01 &&
                        doc.getTotalTradesExecuted() == result.getTotalTradesExecuted() &&
                        doc.getConfigJson().contains("\"gridLevels\":" + config.getGridLevels())) {
                        mongoId = doc.getId();
                        break;
                    }
                }

                System.out.printf("%d. Profit: %.2f%% | MongoDB ID: %s%n",
                    i + 1,
                    result.getProfitPercentage(),
                    mongoId
                );
                System.out.printf("   Config: levels=%d, distance=%.2f%%, takeProfit=%.2f%%, allocation=%.2f%%%n",
                    config.getGridLevels(),
                    config.getGridDistancePercent(),
                    config.getTakeProfitPercent(),
                    config.getPortfolioAllocationPercent()
                );
            }
            System.out.println("==================================================");

            context.close();
        }

        // Display best configuration details
        System.out.println();
        System.out.println("===== BEST CONFIGURATION DETAILS =====");
        System.out.println(summary.getBestResult());
        if (!topTenWithConfigs.isEmpty()) {
            GridBotConfig bestConfig = (GridBotConfig) topTenWithConfigs.get(0).getAlgorithmConfig();
            System.out.printf("%nBest Config: %s%n", bestConfig.getConfigId());
        }
        System.out.println("=======================================");

        // Display filled orders table for best result
        if (!topTenWithConfigs.isEmpty()) {
            System.out.println();
            System.out.println("===== BEST RESULT FILLED ORDERS =====");
            var bestResult = topTenWithConfigs.get(0).getSimulationResult();
            ConsoleReporter.printFilledOrdersTable(bestResult.getFilledOrders());
        }

        log.info("Permutation run completed successfully");
    }
}
