package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.trendfollowing.TrendFollowingAlgorithm;
import com.tradingbot.algorithms.trendfollowing.TrendFollowingConfig;
import com.tradingbot.backtest.data.CandleFileReader;
import com.tradingbot.backtest.engine.BacktestEngine;
import com.tradingbot.backtest.events.LoggingSimulationEventListener;
import com.tradingbot.core.metrics.SimulationResult;
import com.tradingbot.core.models.Candle;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;

/**
 * Single simulation runner for TrendFollowing algorithm.
 *
 * Usage: [candle-file]
 *
 * Examples:
 *   (no arguments)
 *     → Uses default: BTCUSDT-1-365.txt
 *
 *   ETHUSDT
 *     → Uses ETHUSDT-1-365.txt
 *
 *   BTCUSDT-5-90.txt
 *     → Uses custom file
 */
@Slf4j
public class TrendFollowingSingleRunner {

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000");
    private static final String DEFAULT_CANDLE_FILE = "BTCUSDT-1-365.txt";

    public static void main(String[] args) {
        String candleFileName = resolveCandleFileName(args);

        try {
            runSimulation(candleFileName);
        } catch (Exception e) {
            log.error("Simulation failed", e);
            System.err.println("Simulation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String resolveCandleFileName(String[] args) {
        if (args.length == 0) {
            log.info("No arguments provided, using default: {}", DEFAULT_CANDLE_FILE);
            return DEFAULT_CANDLE_FILE;
        }

        String input = args[0];

        // If it ends with .txt, use as-is
        if (input.endsWith(".txt")) {
            return input;
        }

        // Otherwise, append -1-365.txt
        String fileName = input + "-1-365.txt";
        log.info("Trading pair provided, using: {}", fileName);
        return fileName;
    }

    private static void runSimulation(String candleFileName) throws Exception {
        log.info("Starting TrendFollowing single simulation");
        log.info("Candle file: {}", candleFileName);

        // Parse trading pair from filename
        CandleFileReader.CandleFileMetadata metadata = CandleFileReader.parseFileName(candleFileName);
        String tradingPair = metadata.pair();

        log.info("Trading pair: {}", tradingPair);

        // Read candles from classpath
        CandleFileReader reader = new CandleFileReader();
        List<Candle> candles = reader.readCandlesFromClasspath(candleFileName);

        log.info("Loaded {} candles", candles.size());

        // Create TrendFollowing configuration with hardcoded parameters
        TrendFollowingConfig config = TrendFollowingConfig.createDefault();

        log.info("Configuration: {}", config);

        // Create algorithm and pre-calculate indicators
        TrendFollowingAlgorithm algorithm = new TrendFollowingAlgorithm(config);
        algorithm.preCalculateIndicators(candles);

        log.info("Indicators pre-calculated");

        // Create backtest engine with event listener
        BacktestEngine engine = new BacktestEngine(tradingPair, INITIAL_CAPITAL);
        engine.addEventListener(new LoggingSimulationEventListener());

        // Run simulation
        log.info("Running backtest simulation...");
        SimulationResult result = engine.runSimulation(algorithm, candles);

        // Display results
        displayResults(result);

        log.info("Simulation completed successfully");
    }

    private static void displayResults(SimulationResult result) {
        System.out.println();
        System.out.println("========================================");
        System.out.println("  TRENDFOLLOWING SIMULATION RESULTS");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Algorithm:        " + result.getAlgorithmName());
        System.out.println("Trading Pair:     " + result.getTradingPair());
        System.out.println();
        System.out.println("--- PROFITABILITY ---");
        System.out.printf("Initial Balance:  $%.2f%n", result.getInitialBalance());
        System.out.printf("Final Cash:       $%.2f%n", result.getFinalCashBalance());
        System.out.printf("Final Equity:     $%.2f%n", result.getFinalEquity());
        System.out.printf("Profit (Absolute): $%.2f%n", result.getProfitAbsolute());
        System.out.printf("Profit (%%):       %.2f%%%n", result.getProfitPercentage());
        System.out.println();
        System.out.println("--- TRADING ACTIVITY ---");
        System.out.printf("Total Trades:     %d%n", result.getTotalTradesExecuted());
        System.out.printf("Open Positions:   %d%n", result.getOpenPositionsAtEnd());
        System.out.println();
        System.out.println("--- EQUITY METRICS ---");
        System.out.printf("Max Equity:       $%.2f%n", result.getMaxEquityWithPositions());
        System.out.printf("Min Equity:       $%.2f%n", result.getMinEquityWithPositions());
        System.out.printf("Max Cash:         $%.2f%n", result.getMaxCashBalance());
        System.out.printf("Min Cash:         $%.2f%n", result.getMinCashBalance());
        System.out.println();
        System.out.println("--- DRAWDOWN ---");
        System.out.printf("Max Position DD:  %.2f%%%n", result.getMaxPositionDrawdownPercentage());
        System.out.printf("Max Portfolio DD: %.2f%%%n", result.getMaxPortfolioDrawdownPercentage());
        System.out.println();
        System.out.println("--- EXECUTION ---");
        System.out.printf("Interrupted:      %s%n", result.isInterrupted() ? "YES" : "NO");
        if (result.isInterrupted()) {
            System.out.printf("Reason:           %s%n", result.getInterruptionReason());
        }
        System.out.println();
        System.out.println("========================================");
        System.out.println();
    }
}
