package com.tradingbot.backtest.permutation;

import com.tradingbot.core.metrics.SimulationResult;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates and summarizes simulation results from permutation runs.
 */
public class ResultsAggregator {

    /**
     * Summary statistics for a permutation run
     */
    @Value
    @Builder
    public static class Summary {
        int totalSimulations;
        int successfulSimulations;
        int interruptedSimulations;
        int failedSimulations;

        BigDecimal bestProfitPercentage;
        BigDecimal worstProfitPercentage;
        BigDecimal averageProfitPercentage;
        BigDecimal medianProfitPercentage;

        BigDecimal bestProfitAbsolute;
        BigDecimal worstProfitAbsolute;

        BigDecimal averageDrawdown;
        BigDecimal maxDrawdown;

        SimulationResult bestResult;
        SimulationResult worstResult;
        List<SimulationResult> topTenResults;

        long totalExecutionTimeMillis;
        long averageExecutionTimeMillis;

        @Override
        public String toString() {
            return String.format(
                """
                ===== PERMUTATION SUMMARY =====
                Total Simulations: %d
                  Successful: %d (%.1f%%)
                  Interrupted: %d (%.1f%%)
                  Failed: %d (%.1f%%)

                Profit Statistics (Successful Only):
                  Best: %.2f%% ($%.2f)
                  Worst: %.2f%% ($%.2f)
                  Average: %.2f%%
                  Median: %.2f%%

                Drawdown Statistics:
                  Average: %.2f%%
                  Max: %.2f%%

                Execution Time:
                  Total: %d ms (%.2f minutes)
                  Average per simulation: %d ms
                ===============================""",
                totalSimulations,
                successfulSimulations, (successfulSimulations * 100.0 / totalSimulations),
                interruptedSimulations, (interruptedSimulations * 100.0 / totalSimulations),
                failedSimulations, (failedSimulations * 100.0 / totalSimulations),
                bestProfitPercentage, bestProfitAbsolute,
                worstProfitPercentage, worstProfitAbsolute,
                averageProfitPercentage,
                medianProfitPercentage,
                averageDrawdown,
                maxDrawdown,
                totalExecutionTimeMillis, totalExecutionTimeMillis / 60000.0,
                averageExecutionTimeMillis
            );
        }
    }

    /**
     * Aggregate results from simulation tasks
     */
    public static Summary aggregate(List<SimulationTask.Result> results) {
        int total = results.size();
        int successful = (int) results.stream().filter(SimulationTask.Result::isSuccess).count();
        int failed = (int) results.stream().filter(r -> !r.isSuccess()).count();

        // Extract successful simulation results
        List<SimulationResult> successfulResults = results.stream()
            .filter(SimulationTask.Result::isSuccess)
            .map(SimulationTask.Result::getSimulationResult)
            .filter(r -> !r.isInterrupted())
            .collect(Collectors.toList());

        int interrupted = successful - successfulResults.size();

        if (successfulResults.isEmpty()) {
            return Summary.builder()
                .totalSimulations(total)
                .successfulSimulations(0)
                .interruptedSimulations(interrupted)
                .failedSimulations(failed)
                .bestProfitPercentage(BigDecimal.ZERO)
                .worstProfitPercentage(BigDecimal.ZERO)
                .averageProfitPercentage(BigDecimal.ZERO)
                .medianProfitPercentage(BigDecimal.ZERO)
                .bestProfitAbsolute(BigDecimal.ZERO)
                .worstProfitAbsolute(BigDecimal.ZERO)
                .averageDrawdown(BigDecimal.ZERO)
                .maxDrawdown(BigDecimal.ZERO)
                .totalExecutionTimeMillis(results.stream().mapToLong(SimulationTask.Result::getExecutionTimeMillis).sum())
                .averageExecutionTimeMillis(results.stream().mapToLong(SimulationTask.Result::getExecutionTimeMillis).sum() / total)
                .topTenResults(List.of())
                .build();
        }

        // Sort by profit percentage descending
        List<SimulationResult> sortedByProfit = successfulResults.stream()
            .sorted(Comparator.comparing(SimulationResult::getProfitPercentage).reversed())
            .collect(Collectors.toList());

        SimulationResult best = sortedByProfit.get(0);
        SimulationResult worst = sortedByProfit.get(sortedByProfit.size() - 1);
        List<SimulationResult> topTen = sortedByProfit.stream()
            .limit(10)
            .collect(Collectors.toList());

        // Calculate statistics
        BigDecimal avgProfit = successfulResults.stream()
            .map(SimulationResult::getProfitPercentage)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(successfulResults.size()), 2, RoundingMode.HALF_UP);

        BigDecimal medianProfit = calculateMedian(
            successfulResults.stream()
                .map(SimulationResult::getProfitPercentage)
                .collect(Collectors.toList())
        );

        BigDecimal avgDrawdown = successfulResults.stream()
            .map(SimulationResult::getMaxPortfolioDrawdownPercentage)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(successfulResults.size()), 2, RoundingMode.HALF_UP);

        BigDecimal maxDrawdown = successfulResults.stream()
            .map(SimulationResult::getMaxPortfolioDrawdownPercentage)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        long totalTime = results.stream()
            .mapToLong(SimulationTask.Result::getExecutionTimeMillis)
            .sum();

        long avgTime = totalTime / total;

        return Summary.builder()
            .totalSimulations(total)
            .successfulSimulations(successfulResults.size())
            .interruptedSimulations(interrupted)
            .failedSimulations(failed)
            .bestProfitPercentage(best.getProfitPercentage())
            .worstProfitPercentage(worst.getProfitPercentage())
            .averageProfitPercentage(avgProfit)
            .medianProfitPercentage(medianProfit)
            .bestProfitAbsolute(best.getProfitAbsolute())
            .worstProfitAbsolute(worst.getProfitAbsolute())
            .averageDrawdown(avgDrawdown)
            .maxDrawdown(maxDrawdown)
            .bestResult(best)
            .worstResult(worst)
            .topTenResults(topTen)
            .totalExecutionTimeMillis(totalTime)
            .averageExecutionTimeMillis(avgTime)
            .build();
    }

    private static BigDecimal calculateMedian(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<BigDecimal> sorted = values.stream()
            .sorted()
            .collect(Collectors.toList());

        int size = sorted.size();
        if (size % 2 == 0) {
            return sorted.get(size / 2 - 1)
                .add(sorted.get(size / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        } else {
            return sorted.get(size / 2);
        }
    }
}
