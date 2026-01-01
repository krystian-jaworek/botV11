package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticAlgorithm;
import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticConfig;
import com.tradingbot.backtest.data.CandleFileReader;
import com.tradingbot.backtest.permutation.*;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.TradingPair;
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
 * Configuration:
 * - Trading pair is configured via TRADING_PAIR constant
 * - The trading pair determines:
 *   - Candle data file to use (automatically resolved from resources)
 *   - Price precision for orders
 *   - Quantity precision for orders
 *
 * Usage: [save-to-mongodb]
 *
 * Examples:
 *   (no arguments)
 *     → Uses configured TRADING_PAIR, MongoDB enabled (default)
 *
 *   false
 *     → Uses configured TRADING_PAIR, MongoDB disabled
 *
 * Note: MongoDB persistence is ENABLED by default. Use 'false' as argument to disable.
 */
@Slf4j
public class SMAOpportunisticPermutationRunner {

    /**
     * Trading pair configuration.
     * Change this to use different trading pair and precision settings.
     */
    private static final TradingPair TRADING_PAIR = TradingPair.BTCUSDT;

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000");
    private static final String CANDLE_FILE_PATTERN = "%s-1-365.txt";  // Pattern: {PAIR}-1-365.txt

    public static void main(String[] args) {
        // Resolve candle file from configured trading pair
        String candleFileName = String.format(CANDLE_FILE_PATTERN, TRADING_PAIR.getSymbol());

        // MongoDB persistence enabled by default
        boolean saveToMongo = true;
        if (args.length > 0) {
            saveToMongo = Boolean.parseBoolean(args[0]);
        }

        log.info("=== SMA Opportunistic Permutation Runner ===");
        log.info("Trading Pair: {}", TRADING_PAIR.getSymbol());
        log.info("Price Precision: {} decimals", TRADING_PAIR.getPricePrecision());
        log.info("Quantity Precision: {} decimals", TRADING_PAIR.getQuantityPrecision());

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
        // Choose one:
        // - defaultPermutation() = broad search (6804 configs)
        // - finetunePermutation() = local search around best config (1215 configs)
        // - quickPermutation() = fast test (1296 configs)
        SMAOpportunisticParameterPermutation permutation = SMAOpportunisticParameterPermutation.finetunePermutation();
        List<SMAOpportunisticConfig> configurations = permutation.generateConfigurations();

        log.info("Generated {} configurations", configurations.size());
        log.info("FINE-TUNE MODE: Local search around best found configuration");
        log.info("Center values: SMA=900, Size=7.5%, Dev=2.5%, TP=3%, Cool=12h, MinDrop=1%, AggrDCA=2.5%");
        log.info("Parameter ranges:");
        log.info("  SMA period: 780-1020 (step 60) - 5 values");
        log.info("  Position size (Y): 7.0%-8.0% (step 0.5) - 3 values");
        log.info("  SMA deviation (X): 2.25%-2.75% (step 0.25) - 3 values");
        log.info("  Take profit (Z): 2.75%-3.25% (step 0.25) - 3 values");
        log.info("  Cooldown (B): 12h (fixed) - 1 value");
        log.info("  Min price drop: 0.75%-1.25% (step 0.25) - 3 values");
        log.info("  Aggressive DCA: 2.0%-3.0% (step 0.5) - 3 values");
        log.info("Early stopping enabled: loss > 50% after 50% of simulation OR no trades after 25%");

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

            System.out.printf("%d. Profit: %.2f%% | Trades: %d%n",
                i + 1,
                result.getProfitPercentage(),
                result.getTotalTradesExecuted()
            );
            System.out.printf("   Max Portfolio DD: %.2f%% | Max Position DD: %.2f%% | Min Cash: $%.2f%n",
                result.getMaxPortfolioDrawdownPercentage(),
                result.getMaxPositionDrawdownPercentage(),
                result.getMinCashBalance()
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
