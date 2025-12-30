package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.recalculatinggrid.RecalculatingGridAlgorithm;
import com.tradingbot.algorithms.recalculatinggrid.RecalculatingGridConfig;
import com.tradingbot.backtest.data.BacktestMarketDataProvider;
import com.tradingbot.backtest.data.CandleFileReader;
import com.tradingbot.backtest.engine.BacktestEngine;
import com.tradingbot.backtest.executor.MockOrderExecutor;
import com.tradingbot.backtest.reporting.ConsoleReporter;
import com.tradingbot.core.metrics.SimulationResult;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.Portfolio;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;

/**
 * Single simulation runner for Recalculating Grid Trading algorithm.
 *
 * Runs a single backtest with default or custom RecalculatingGrid configuration.
 *
 * Usage:
 *   java -cp bot-backtest/target/bot-backtest.jar \
 *     com.tradingbot.backtest.runners.RecalculatingGridSingleRunner [candle-file]
 *
 * Examples:
 *   RecalculatingGridSingleRunner
 *     → Uses default BTCUSDT-1-365.txt
 *
 *   RecalculatingGridSingleRunner ETHUSDT-1-365.txt
 *     → Uses custom file
 */
@Slf4j
public class RecalculatingGridSingleRunner {

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000");
    private static final String DEFAULT_CANDLE_FILE = "BTCUSDT-1-365.txt";

    public static void main(String[] args) {
        String candleFileName = args.length > 0 ? args[0] : DEFAULT_CANDLE_FILE;

        try {
            runSingleSimulation(candleFileName);
        } catch (Exception e) {
            log.error("Simulation failed", e);
            System.err.println("Simulation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runSingleSimulation(String candleFileName) throws Exception {
        log.info("Starting Recalculating Grid single simulation");
        log.info("Candle file: {}", candleFileName);

        // Parse trading pair from filename
        CandleFileReader.CandleFileMetadata metadata = CandleFileReader.parseFileName(candleFileName);
        String tradingPair = metadata.pair();

        // Read candles
        CandleFileReader reader = new CandleFileReader();
        List<Candle> candles = reader.readCandlesFromClasspath(candleFileName);
        log.info("Loaded {} candles for {}", candles.size(), tradingPair);

        // Create configuration
        RecalculatingGridConfig config = RecalculatingGridConfig.defaultConfig();
        log.info("Using config: {}", config.getConfigId());

        // Create algorithm
        RecalculatingGridAlgorithm algorithm = new RecalculatingGridAlgorithm(config);

        // Create backtest engine with event listener
        BacktestEngine engine = new BacktestEngine(tradingPair, INITIAL_CAPITAL);
        engine.addEventListener(new ConsoleReporter());

        // Run simulation
        log.info("Running simulation...");
        SimulationResult result = engine.runSimulation(algorithm, candles);

        // Display results
        System.out.println();
        System.out.println("========================================");
        System.out.println("  RECALCULATING GRID SIMULATION RESULTS");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Algorithm:        " + algorithm.getName());
        System.out.println("Trading Pair:     " + tradingPair);
        System.out.println("Configuration:    " + config.getConfigId());
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
        System.out.println("========================================");

        log.info("Simulation completed successfully");
    }
}
