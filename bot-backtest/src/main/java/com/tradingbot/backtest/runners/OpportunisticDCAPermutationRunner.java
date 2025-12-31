package com.tradingbot.backtest.runners;

import com.tradingbot.algorithms.opportunisticdca.OpportunisticDCAAlgorithm;
import com.tradingbot.algorithms.opportunisticdca.OpportunisticDCAConfig;
import com.tradingbot.backtest.data.CandleFileReader;
import com.tradingbot.backtest.engine.BacktestEngine;
import com.tradingbot.backtest.permutation.OpportunisticDCAParameterPermutation;
import com.tradingbot.core.metrics.SimulationResult;
import com.tradingbot.core.models.Candle;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Permutation runner for Opportunistic DCA algorithm.
 * Tests multiple parameter combinations.
 */
@Slf4j
public class OpportunisticDCAPermutationRunner {

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000");
    private static final String DEFAULT_CANDLE_FILE = "BTCUSDT-1-365.txt";

    public static void main(String[] args) {
        String candleFileName = args.length > 0 ? args[0] : DEFAULT_CANDLE_FILE;
        boolean useCompact = args.length > 1 && args[1].equals("compact");

        try {
            runPermutations(candleFileName, useCompact);
        } catch (Exception e) {
            log.error("Permutation run failed", e);
            System.err.println("Permutation run failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runPermutations(String candleFileName, boolean useCompact) throws Exception {
        log.info("Starting Opportunistic DCA permutation run");
        log.info("Candle file: {}", candleFileName);
        log.info("Mode: {}", useCompact ? "compact" : "full");

        // Parse trading pair
        CandleFileReader.CandleFileMetadata metadata = CandleFileReader.parseFileName(candleFileName);
        String tradingPair = metadata.pair();
        log.info("Trading pair: {}", tradingPair);

        // Read candles
        CandleFileReader reader = new CandleFileReader();
        List<Candle> candles = reader.readCandlesFromClasspath(candleFileName);
        log.info("Loaded {} candles", candles.size());

        // Generate configurations
        OpportunisticDCAParameterPermutation permutation = useCompact
            ? OpportunisticDCAParameterPermutation.compactPermutation()
            : OpportunisticDCAParameterPermutation.defaultPermutation();

        List<OpportunisticDCAConfig> configs = permutation.generateConfigurations();
        log.info("Generated {} configurations", configs.size());

        // Run simulations
        List<PermutationResult> results = new ArrayList<>();
        int configNumber = 0;

        for (OpportunisticDCAConfig config : configs) {
            configNumber++;
            log.info("Running configuration {}/{}: {}", configNumber, configs.size(), config.getConfigId());

            try {
                OpportunisticDCAAlgorithm algorithm = new OpportunisticDCAAlgorithm(config);
                BacktestEngine engine = new BacktestEngine(tradingPair, INITIAL_CAPITAL);
                SimulationResult result = engine.runSimulation(algorithm, candles);

                results.add(new PermutationResult(config, result));

                log.info("  → Profit: {:.2f}%, Drawdown: {:.2f}%, Trades: {}",
                    result.getProfitPercentage(),
                    result.getMaxPortfolioDrawdownPercentage(),
                    result.getTotalTradesExecuted());

            } catch (Exception e) {
                log.error("  → FAILED: {}", e.getMessage());
            }
        }

        // Display results
        displayResults(results);
    }

    private static void displayResults(List<PermutationResult> results) {
        System.out.println("\n" + "=".repeat(120));
        System.out.println("PERMUTATION RESULTS - Opportunistic DCA");
        System.out.println("=".repeat(120));
        System.out.printf("Total configurations tested: %d%n", results.size());
        System.out.println();

        // Sort by profit percentage (descending)
        results.sort(Comparator.comparing(r -> r.result.getProfitPercentage(), Comparator.reverseOrder()));

        // Display top 10
        System.out.println("TOP 10 CONFIGURATIONS (by profit %):");
        System.out.println("-".repeat(120));
        System.out.printf("%-5s %-40s %12s %12s %10s %10s%n",
            "Rank", "Config ID", "Profit %", "Drawdown %", "Trades", "Final $");
        System.out.println("-".repeat(120));

        for (int i = 0; i < Math.min(10, results.size()); i++) {
            PermutationResult r = results.get(i);
            System.out.printf("%-5d %-40s %11.2f%% %11.2f%% %10d %10.2f%n",
                i + 1,
                r.config.getConfigId(),
                r.result.getProfitPercentage(),
                r.result.getMaxPortfolioDrawdownPercentage(),
                r.result.getTotalTradesExecuted(),
                r.result.getFinalEquity());
        }

        System.out.println("-".repeat(120));

        // Display statistics
        double avgProfit = results.stream()
            .mapToDouble(r -> r.result.getProfitPercentage().doubleValue())
            .average()
            .orElse(0.0);

        double avgDrawdown = results.stream()
            .mapToDouble(r -> r.result.getMaxPortfolioDrawdownPercentage().doubleValue())
            .average()
            .orElse(0.0);

        double avgTrades = results.stream()
            .mapToInt(r -> r.result.getTotalTradesExecuted())
            .average()
            .orElse(0.0);

        System.out.println();
        System.out.println("STATISTICS:");
        System.out.printf("  Average Profit:   %.2f%%%n", avgProfit);
        System.out.printf("  Average Drawdown: %.2f%%%n", avgDrawdown);
        System.out.printf("  Average Trades:   %.0f%n", avgTrades);
        System.out.println("=".repeat(120));
    }

    private static class PermutationResult {
        final OpportunisticDCAConfig config;
        final SimulationResult result;

        PermutationResult(OpportunisticDCAConfig config, SimulationResult result) {
            this.config = config;
            this.result = result;
        }
    }
}
