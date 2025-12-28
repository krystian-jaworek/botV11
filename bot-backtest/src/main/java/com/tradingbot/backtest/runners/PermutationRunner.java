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
 * Usage: java -jar bot-backtest.jar permutation <candle-file> [save-to-mongodb]
 *
 * Example:
 *   java -jar bot-backtest.jar permutation BTCUSDT-1-365.txt true
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

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar bot-backtest.jar permutation <candle-file> [save-to-mongodb]");
            System.err.println("Example: java -jar bot-backtest.jar permutation BTCUSDT-1-365.txt true");
            System.err.println("Note: Candle file should be in resources directory");
            System.exit(1);
        }

        String candleFileName = args[1];  // args[0] is "permutation"
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
