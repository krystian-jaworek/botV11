package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.gridbot.GridBotAlgorithm;
import com.tradingbot.algorithms.gridbot.GridBotConfig;
import com.tradingbot.backtest.data.CandleFileReader;
import com.tradingbot.backtest.engine.BacktestEngine;
import com.tradingbot.backtest.reporting.ConsoleReporter;
import com.tradingbot.core.metrics.SimulationResult;
import com.tradingbot.core.models.Candle;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Main runner for single simulation with hardcoded parameters.
 *
 * Usage: java -jar bot-backtest.jar [trading-pair|candle-file]
 *
 * Examples:
 *   java -jar bot-backtest.jar                    # Uses default: BTCUSDT-1-365.txt
 *   java -jar bot-backtest.jar BTCUSDT            # Uses: BTCUSDT-1-365.txt
 *   java -jar bot-backtest.jar ETHUSDT            # Uses: ETHUSDT-1-365.txt
 *   java -jar bot-backtest.jar BTCUSDT-5-90.txt   # Uses: BTCUSDT-5-90.txt (custom)
 *
 * The candle file should be placed in src/main/resources/
 *
 * Hardcoded parameters (from requirements):
 * - 20 grid levels
 * - 1% distance between levels
 * - 2% take profit
 * - 70% portfolio allocation
 * - $10,000 initial capital
 */
@Slf4j
public class SingleSimulationRunner {

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

    /**
     * Resolve candle file name from arguments.
     * - No args: BTCUSDT-1-365.txt
     * - Trading pair (e.g., "BTCUSDT"): BTCUSDT-1-365.txt
     * - Full file name (e.g., "BTCUSDT-5-90.txt"): as is
     */
    private static String resolveCandleFileName(String[] args) {
        if (args.length == 0) {
            log.info("No arguments provided, using default: {}", DEFAULT_CANDLE_FILE);
            return DEFAULT_CANDLE_FILE;
        }

        String input = args[0];

        // If already ends with .txt, use as is
        if (input.endsWith(".txt")) {
            return input;
        }

        // Otherwise, assume it's a trading pair and append -1-365.txt
        String fileName = input + "-1-365.txt";
        log.info("Trading pair provided, using: {}", fileName);
        return fileName;
    }

    private static void runSimulation(String candleFileName) throws IOException {
        log.info("Starting single simulation");
        log.info("Candle file: {}", candleFileName);

        // Parse trading pair from file name
        CandleFileReader.CandleFileMetadata metadata = CandleFileReader.parseFileName(candleFileName);
        String tradingPair = metadata.pair();

        log.info("Trading pair: {}", tradingPair);
        log.info("Interval: {} minutes", metadata.intervalMinutes());
        log.info("Days: {}", metadata.days());

        // Read candles from classpath (resources)
        CandleFileReader reader = new CandleFileReader();
        List<Candle> candles = reader.readCandlesFromClasspath(candleFileName);

        log.info("Loaded {} candles", candles.size());

        // Create algorithm with hardcoded config
        GridBotConfig config = GridBotConfig.defaultConfig();
        GridBotAlgorithm algorithm = new GridBotAlgorithm(config);

        log.info("Algorithm configuration: {}", config.getConfigId());

        // Create backtest engine
        BacktestEngine engine = new BacktestEngine(tradingPair, INITIAL_CAPITAL);

        // Add console reporter for detailed output
        ConsoleReporter reporter = new ConsoleReporter(true);
        engine.addEventListener(reporter);

        // Run simulation
        log.info("Starting backtest...");
        SimulationResult result = engine.runSimulation(algorithm, candles);

        // Result is already printed by ConsoleReporter
        log.info("Simulation completed successfully");

        // Exit with appropriate code
        if (result.isInterrupted()) {
            System.exit(2);  // Exit code 2 = interrupted
        } else {
            System.exit(0);  // Exit code 0 = success
        }
    }
}
