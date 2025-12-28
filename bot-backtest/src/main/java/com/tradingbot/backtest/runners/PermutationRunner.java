package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.gridbot.GridBotAlgorithm;
import com.tradingbot.algorithms.gridbot.GridBotConfig;
import com.tradingbot.backtest.data.CandleFileReader;
import com.tradingbot.backtest.permutation.*;
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
 * Main runner for permutation mode (parallel simulations with parameter sweeps).
 *
 * Usage: java -jar bot-backtest.jar permutation [candle-file] [save-to-mongodb]
 *
 * Examples:
 *   java -jar bot-backtest.jar permutation
 *     → Uses default: BTCUSDT-1-365.txt, no MongoDB
 *
 *   java -jar bot-backtest.jar permutation ETHUSDT
 *     → Uses ETHUSDT-1-365.txt, no MongoDB
 *
 *   java -jar bot-backtest.jar permutation BTCUSDT-5-90.txt
 *     → Uses custom file, no MongoDB
 *
 *   java -jar bot-backtest.jar permutation BTCUSDT true
 *     → Uses BTCUSDT-1-365.txt, saves to MongoDB
 *
 *   java -jar bot-backtest.jar permutation BTCUSDT-1-365.txt true
 *     → Uses custom file, saves to MongoDB
 *
 * The candle file should be placed in src/main/resources/
 *
 * Parameter ranges (default):
 * - Grid levels: 10-30 (step 5) = 5 values
 * - Grid distance: 0.5-2.0% (step 0.5) = 4 values
 * - Take profit: 1.0-3.0% (step 0.5) = 5 values
 * - Portfolio allocation: 60-80% (step 10) = 3 values
 * Total: 5 * 4 * 5 * 3 = 300 simulations
 */
@Slf4j
public class PermutationRunner {

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000");
    private static final String DEFAULT_CANDLE_FILE = "BTCUSDT-1-365.txt";

    public static void main(String[] args) {
        // args[0] is "permutation", args[1] is candle file (optional), args[2] is save flag (optional)
        String candleFileName = resolveCandleFileName(args);
        boolean saveToMongo = args.length > 2 && Boolean.parseBoolean(args[2]);

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
     * @param args Command line arguments where args[0] is "permutation"
     * @return The resolved candle file name
     */
    private static String resolveCandleFileName(String[] args) {
        // args.length == 1: just "permutation" → use default
        if (args.length == 1) {
            log.info("No candle file provided, using default: {}", DEFAULT_CANDLE_FILE);
            return DEFAULT_CANDLE_FILE;
        }

        // args.length >= 2: args[1] contains file or trading pair
        String input = args[1];

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

        log.info("Permutation run completed successfully");
    }
}
