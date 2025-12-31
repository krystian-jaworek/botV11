package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticAlgorithm;
import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticConfig;
import com.tradingbot.backtest.data.CandleFileReader;
import com.tradingbot.backtest.permutation.*;
import com.tradingbot.core.models.Candle;
import com.tradingbot.persistence.config.MongoConfig;
import com.tradingbot.persistence.repositories.DynamicSimulationResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * SMA Opportunistic permutation runner for parallel parameter sweep simulations.
 *
 * Runs simulations with different SMA period values using Virtual Threads.
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
 * Note: MongoDB persistence is ENABLED by default. Use 'false' as second argument to disable.
 *
 * SMA parameter range (default):
 * - SMA period: 600-2040 (step 60) = 25 values
 * Total: 25 simulations
 */
@Slf4j
public class SMAOpportunisticPermutationRunner {

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
     */
    private static String resolveCandleFileName(String[] args) {
        if (args.length == 0) {
            log.info("No arguments provided, using default: {}", DEFAULT_CANDLE_FILE);
            return DEFAULT_CANDLE_FILE;
        }

        String input = args[0];

        // If ends with .txt, use as is
        if (input.endsWith(".txt")) {
            return input;
        }

        // Otherwise, it's a trading pair - append -1-365.txt
        String fileName = input + "-1-365.txt";
        log.info("Trading pair provided, using: {}", fileName);
        return fileName;
    }

    private static void runPermutations(String candleFileName, boolean saveToMongo) throws Exception {
        log.info("Starting SMA Opportunistic permutation run");
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
        SMAOpportunisticParameterPermutation permutation = SMAOpportunisticParameterPermutation.defaultPermutation();
        List<SMAOpportunisticConfig> configurations = permutation.generateConfigurations();

        log.info("Generated {} configurations", configurations.size());
        log.info("Parameter range:");
        log.info("  SMA period: 600-2040 (step 60)");

        // Create simulation tasks
        List<SimulationTask> tasks = new ArrayList<>();
        for (int i = 0; i < configurations.size(); i++) {
            SMAOpportunisticConfig config = configurations.get(i);
            SMAOpportunisticAlgorithm algorithm = new SMAOpportunisticAlgorithm(config);

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

            log.info("MongoDB persistence enabled (collection: SMAOpportunistic-{})", tradingPair);
        }

        // Create parallel executor
        BatchResultsPersister finalPersister = persister;
        ParallelSimulationExecutor executor = new ParallelSimulationExecutor(
            result -> {
                if (finalPersister != null) {
                    finalPersister.persist(result);
                }
            },
            10  // Report every 10 simulations
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
            SMAOpportunisticConfig config = (SMAOpportunisticConfig) taskResult.getAlgorithmConfig();

            System.out.printf("%d. Profit: %.2f%% | Max DD: %.2f%% | Trades: %d%n",
                i + 1,
                result.getProfitPercentage(),
                result.getMaxPortfolioDrawdownPercentage(),
                result.getTotalTradesExecuted()
            );
            System.out.printf("   Config: %s%n", config.getConfigId());
            System.out.printf("   Final Balance: $%.2f | Profit: $%.2f%n",
                result.getFinalEquity(),
                result.getProfitAbsolute()
            );
            System.out.println();
        }

        log.info("\nPermutation run completed successfully");
        System.exit(0);
    }
}
