package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.opportunisticdca.OpportunisticDCAAlgorithm;
import com.tradingbot.algorithms.opportunisticdca.OpportunisticDCAConfig;
import com.tradingbot.backtest.data.CandleFileReader;
import com.tradingbot.backtest.engine.BacktestEngine;
import com.tradingbot.backtest.reporting.ConsoleReporter;
import com.tradingbot.core.metrics.SimulationResult;
import com.tradingbot.core.models.Candle;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;

/**
 * Single-run runner for Opportunistic DCA algorithm.
 *
 * Usage: [candle-file]
 *
 * Examples:
 *   (no arguments) → Uses default: BTCUSDT-1-365.txt
 *   ETHUSDT → Uses ETHUSDT-1-365.txt
 *   BTCUSDT-5-90.txt → Uses custom file
 */
@Slf4j
public class OpportunisticDCARunner {

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

    private static void runSimulation(String candleFileName) throws Exception {
        log.info("Starting Opportunistic DCA simulation");
        log.info("Candle file: {}", candleFileName);

        // Parse trading pair
        CandleFileReader.CandleFileMetadata metadata = CandleFileReader.parseFileName(candleFileName);
        String tradingPair = metadata.pair();
        log.info("Trading pair: {}", tradingPair);

        // Read candles
        CandleFileReader reader = new CandleFileReader();
        List<Candle> candles = reader.readCandlesFromClasspath(candleFileName);
        log.info("Loaded {} candles", candles.size());

        // Create algorithm with default config
        OpportunisticDCAConfig config = OpportunisticDCAConfig.defaultConfig();
        OpportunisticDCAAlgorithm algorithm = new OpportunisticDCAAlgorithm(config);

        log.info("Algorithm config: {}", config.getConfigId());

        // Create backtest engine
        BacktestEngine engine = new BacktestEngine(tradingPair, INITIAL_CAPITAL);

        // Add console reporter as event listener
        ConsoleReporter reporter = new ConsoleReporter();
        engine.addEventListener(reporter);

        // Run simulation
        SimulationResult result = engine.runSimulation(algorithm, candles);

        // Display results
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SIMULATION RESULTS - Opportunistic DCA");
        System.out.println("=".repeat(80));
        System.out.println("Algorithm:        " + algorithm.getName());
        System.out.println("Trading Pair:     " + tradingPair);
        System.out.println("Configuration:    " + config.getConfigId());
        System.out.println();
        System.out.println("--- PROFITABILITY ---");
        System.out.printf("Initial Balance:  $%.2f%n", result.getInitialBalance());
        System.out.printf("Final Equity:     $%.2f%n", result.getFinalEquity());
        System.out.printf("Profit:           $%.2f (%.2f%%)%n",
            result.getProfitAbsolute(), result.getProfitPercentage());
        System.out.println();
        System.out.println("--- RISK ---");
        System.out.printf("Max Drawdown:     %.2f%%%n", result.getMaxPortfolioDrawdownPercentage());
        System.out.println();
        System.out.println("--- ACTIVITY ---");
        System.out.printf("Total Trades:     %d%n", result.getTotalTradesExecuted());
        System.out.printf("Open Positions:   %d%n", result.getOpenPositionsAtEnd());
        System.out.println("=".repeat(80));

        // Display filled orders table if available
        if (result.getFilledOrders() != null && !result.getFilledOrders().isEmpty()) {
            System.out.println();
            ConsoleReporter.printFilledOrdersTable(result.getFilledOrders());
        }
    }
}
