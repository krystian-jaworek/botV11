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
 * Usage: java -jar bot-backtest.jar <data-directory> <candle-file>
 *
 * Example:
 *   java -jar bot-backtest.jar ./data BTCUSDT-1-365.txt
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

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar bot-backtest.jar <data-directory> <candle-file>");
            System.err.println("Example: java -jar bot-backtest.jar ./data BTCUSDT-1-365.txt");
            System.exit(1);
        }

        String dataDirectory = args[0];
        String candleFileName = args[1];

        try {
            runSimulation(dataDirectory, candleFileName);
        } catch (Exception e) {
            log.error("Simulation failed", e);
            System.err.println("Simulation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runSimulation(String dataDirectory, String candleFileName) throws IOException {
        log.info("Starting single simulation");
        log.info("Data directory: {}", dataDirectory);
        log.info("Candle file: {}", candleFileName);

        // Parse trading pair from file name
        CandleFileReader.CandleFileMetadata metadata = CandleFileReader.parseFileName(candleFileName);
        String tradingPair = metadata.pair();

        log.info("Trading pair: {}", tradingPair);
        log.info("Interval: {} minutes", metadata.intervalMinutes());
        log.info("Days: {}", metadata.days());

        // Read candles
        Path candleFilePath = Paths.get(dataDirectory, candleFileName);
        CandleFileReader reader = new CandleFileReader();
        List<Candle> candles = reader.readCandles(candleFilePath);

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
