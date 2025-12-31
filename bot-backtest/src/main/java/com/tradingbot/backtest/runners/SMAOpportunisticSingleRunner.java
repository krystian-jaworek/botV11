package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticAlgorithm;
import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticConfig;
import com.tradingbot.backtest.data.CandleFileReader;
import com.tradingbot.backtest.engine.BacktestEngine;
import com.tradingbot.backtest.reporting.ConsoleReporter;
import com.tradingbot.core.metrics.SimulationResult;
import com.tradingbot.core.models.Candle;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * SMA Opportunistic single run simulation.
 *
 * Usage: java -cp bot-backtest.jar com.tradingbot.backtest.runners.SMAOpportunisticSingleRunner [candle-file]
 *
 * Examples:
 *   java -cp ... SMAOpportunisticSingleRunner                    # Uses default: BTCUSDT-1-365.txt
 *   java -cp ... SMAOpportunisticSingleRunner BTCUSDT-1-365.txt  # Custom file
 *
 * Default parameters:
 * - SMA period: 1440 (24h with 1m candles)
 * - $10,000 initial capital
 */
@Slf4j
public class SMAOpportunisticSingleRunner {

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000");
    private static final String DEFAULT_CANDLE_FILE = "BTCUSDT-1-365.txt";

    public static void main(String[] args) {
        String candleFileName = args.length > 0 ? args[0] : DEFAULT_CANDLE_FILE;

        try {
            runSimulation(candleFileName);
        } catch (Exception e) {
            log.error("Simulation failed", e);
            System.err.println("Simulation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runSimulation(String candleFileName) throws IOException {
        log.info("Starting SMA Opportunistic single simulation");
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

        // Create algorithm with default config
        SMAOpportunisticConfig config = SMAOpportunisticConfig.defaultConfig();
        SMAOpportunisticAlgorithm algorithm = new SMAOpportunisticAlgorithm(config);

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
