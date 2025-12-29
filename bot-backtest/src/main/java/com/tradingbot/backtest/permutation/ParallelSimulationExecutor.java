package com.tradingbot.backtest.permutation;

import com.tradingbot.backtest.engine.BacktestEngine;
import com.tradingbot.core.metrics.SimulationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes simulations in parallel using Java 21 Virtual Threads.
 *
 * Performance characteristics:
 * - Virtual threads are lightweight (millions can be created)
 * - Each simulation runs in its own virtual thread
 * - No thread pool size limits
 * - Automatic work-stealing scheduler
 */
@Slf4j
public class ParallelSimulationExecutor {

    private final ResultConsumer resultConsumer;
    private final int progressReportInterval;

    /**
     * Interface for consuming simulation results as they complete
     */
    public interface ResultConsumer {
        void onResult(SimulationTask.Result result);
    }

    public ParallelSimulationExecutor(ResultConsumer resultConsumer, int progressReportInterval) {
        this.resultConsumer = resultConsumer;
        this.progressReportInterval = progressReportInterval;
    }

    public ParallelSimulationExecutor(ResultConsumer resultConsumer) {
        this(resultConsumer, 100); // Report every 100 simulations
    }

    /**
     * Execute all simulation tasks in parallel using virtual threads.
     *
     * @param tasks List of simulation tasks to execute
     * @return List of all results
     */
    public List<SimulationTask.Result> executeAll(List<SimulationTask> tasks) {
        log.info("Starting parallel execution of {} simulations using Virtual Threads", tasks.size());

        ConcurrentLinkedQueue<SimulationTask.Result> results = new ConcurrentLinkedQueue<>();
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // Create virtual thread executor
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // Submit all tasks
            for (SimulationTask task : tasks) {
                executor.submit(() -> {
                    SimulationTask.Result result = executeTask(task);
                    results.add(result);

                    // Update counters
                    int completed = completedCount.incrementAndGet();
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }

                    // Consume result
                    try {
                        resultConsumer.onResult(result);
                    } catch (Exception e) {
                        log.error("Error in result consumer for task {}", task.getTaskId(), e);
                    }

                    // Progress reporting
                    if (completed % progressReportInterval == 0) {
                        double percentComplete = (completed * 100.0) / tasks.size();
                        long elapsed = System.currentTimeMillis() - startTime;
                        double avgTimePerSim = elapsed / (double) completed;
                        long estimatedRemaining = (long) (avgTimePerSim * (tasks.size() - completed));

                        log.info("Progress: {}/{} ({}%) - Success: {} | Failed: {} | Avg: {}ms | ETA: {}s",
                            completed,
                            tasks.size(),
                            String.format("%.1f", percentComplete),
                            successCount.get(),
                            failureCount.get(),
                            Math.round(avgTimePerSim),
                            estimatedRemaining / 1000);
                    }
                });
            }

            // Shutdown and wait for completion
            executor.shutdown();
            try {
                boolean finished = executor.awaitTermination(24, TimeUnit.HOURS);
                if (!finished) {
                    log.error("Executor did not finish within 24 hours");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Executor interrupted", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        double avgTime = totalTime / (double) tasks.size();

        log.info("Parallel execution completed in {}ms (avg {}ms per simulation)",
            totalTime, Math.round(avgTime));
        log.info("Results: {} successful, {} failed, {} total",
            successCount.get(), failureCount.get(), results.size());

        return new ArrayList<>(results);
    }

    /**
     * Execute a single simulation task
     */
    private SimulationTask.Result executeTask(SimulationTask task) {
        long taskStart = System.currentTimeMillis();

        try {
            // Create backtest engine (no event listeners for performance)
            BacktestEngine engine = new BacktestEngine(
                task.getTradingPair(),
                task.getInitialBalance()
            );

            // Disable candle progress logging for parallel execution
            // (permutation progress is logged by ParallelSimulationExecutor)
            engine.setEnableProgressLogging(false);

            // Run simulation
            SimulationResult result = engine.runSimulation(
                task.getAlgorithm(),
                task.getCandles()
            );

            long executionTime = System.currentTimeMillis() - taskStart;

            // Get algorithm config for persistence
            Object algorithmConfig = task.getAlgorithm().getConfig();

            return SimulationTask.Result.success(task.getTaskId(), result, algorithmConfig, executionTime);

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - taskStart;
            log.error("Simulation task {} failed", task.getTaskId(), e);
            return SimulationTask.Result.failure(
                task.getTaskId(),
                e.getMessage(),
                executionTime
            );
        }
    }
}
