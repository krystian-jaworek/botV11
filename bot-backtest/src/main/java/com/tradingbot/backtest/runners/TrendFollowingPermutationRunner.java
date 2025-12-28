package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.trendfollowing.TrendFollowingAlgorithm;
import com.tradingbot.algorithms.trendfollowing.TrendFollowingConfig;
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
 * TrendFollowing-specific permutation runner for parallel parameter sweep simulations.
 *
 * Runs ~768 TrendFollowing simulations with different parameter combinations using Virtual Threads.
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
 *     → Uses BTCUSDT-1-365.txt, MongoDB DISABLED (recommended for testing)
 *
 *   BTCUSDT-1-365.txt false
 *     → Uses custom file, MongoDB DISABLED
 *
 * IMPORTANT - Memory Configuration:
 *   If running with MongoDB enabled (large datasets), you may need to increase JVM heap:
 *     java -Xmx4g -jar bot-backtest.jar TrendFollowingPermutationRunner BTCUSDT
 *
 *   For testing without MongoDB (faster, less memory):
 *     java -jar bot-backtest.jar TrendFollowingPermutationRunner BTCUSDT false
 *
 * Note: MongoDB batch size is set to 50 results per flush to prevent OOM.
 *
 * The candle file should be placed in src/main/resources/
 *
 * TrendFollowing parameter ranges (reduced to ~768):
 * - EMA fast primary: 50, 100 (2 values)
 * - EMA slow primary: 150, 200 (2 values) - REDUCED
 * - EMA fast secondary: 20, 30 (2 values)
 * - EMA slow secondary: 50, 100 (2 values)
 * - MACD: 12/26/9 (1 combination) - FIXED to defaults
 * - Higher lows periods: 3, 4 (2 values) - REDUCED
 * - Swing detection: 5, 7 (2 values)
 * - Histogram growth: 2, 3 (2 values)
 * - TP options: 2 (conservative, aggressive)
 * - Stop loss: 15%, 20%, 25% (3 values)
 * - Trailing activation: 50% (1 value) - FIXED
 * - Trailing distance: 20% (1 value) - FIXED
 * Total: 4 × 4 × 1 × 4 × 2 × 2 × 3 × 1 = 768 valid combinations
 */
@Slf4j
public class TrendFollowingPermutationRunner {

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
        log.info("Starting TrendFollowing permutation run");
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
        TrendFollowingParameterPermutation permutation = TrendFollowingParameterPermutation.defaultPermutation();
        int estimatedCount = permutation.calculatePermutationCount();
        log.info("Estimated configurations: ~{}", estimatedCount);

        List<TrendFollowingConfig> configurations = permutation.generateConfigurations();

        log.info("Generated {} configurations", configurations.size());
        log.info("Parameter ranges:");
        log.info("  EMA fast primary: {}", permutation.getEmaFastPrimaryRangeStr());
        log.info("  EMA slow primary: {}", permutation.getEmaSlowPrimaryRangeStr());
        log.info("  Stop Loss: {}", permutation.getStopLossPctRangeStr());

        // Create simulation tasks
        // NOTE: Stores only configs, not algorithm instances, to avoid OOM
        // Algorithms are created on-demand in ParallelSimulationExecutor.executeTask()
        List<SimulationTask> tasks = new ArrayList<>();
        for (int i = 0; i < configurations.size(); i++) {
            TrendFollowingConfig config = configurations.get(i);

            SimulationTask task = SimulationTask.builder()
                .taskId(i)
                .tradingPair(tradingPair)
                .algorithmConfig(config)  // Only config, not algorithm instance
                .algorithmClass(TrendFollowingAlgorithm.class)  // Class for instantiation
                .candles(candles)
                .initialBalance(INITIAL_CAPITAL)
                .build();

            tasks.add(task);
        }

        log.info("Created {} simulation tasks (algorithms will be instantiated per-task)", tasks.size());

        // Setup MongoDB persistence if requested
        BatchResultsPersister persister = null;
        if (saveToMongo) {
            log.info("Initializing MongoDB connection...");
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.register(MongoConfig.class);
            context.refresh();

            DynamicSimulationResultRepository repository = context.getBean(DynamicSimulationResultRepository.class);

            // REDUCED batch size to prevent OOM in MongoDB driver
            // With 768 parallel simulations, smaller batches are safer
            persister = new BatchResultsPersister(
                repository,
                50,   // Batch size: flush every 50 results (was 1000)
                5     // Flush interval: 5 seconds (was 10)
            );

            log.info("MongoDB persistence enabled (collection: TrendFollowing-{}, batchSize: 50)", tradingPair);
        } else {
            log.info("MongoDB persistence DISABLED - results will only be kept in memory");
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

        // Aggregate and display results
        log.info("Aggregating results...");
        ResultsAggregator.Summary summary = ResultsAggregator.aggregate(results);

        System.out.println();
        System.out.println(summary);
        System.out.println();

        // Display top 10 configurations
        System.out.println("===== TOP 10 CONFIGURATIONS =====");
        for (int i = 0; i < summary.getTopTenResults().size(); i++) {
            var result = summary.getTopTenResults().get(i);
            System.out.printf("%d. Profit: %.2f%% | Max DD: %.2f%% | Trades: %d%n",
                i + 1,
                result.getProfitPercentage(),
                result.getMaxPortfolioDrawdownPercentage(),
                result.getTotalTradesExecuted()
            );
        }
        System.out.println("=================================");

        // Display best configuration details
        System.out.println();
        System.out.println("===== BEST CONFIGURATION DETAILS =====");
        System.out.println(summary.getBestResult());
        System.out.println("=======================================");

        log.info("TrendFollowing permutation run completed successfully");
    }
}
