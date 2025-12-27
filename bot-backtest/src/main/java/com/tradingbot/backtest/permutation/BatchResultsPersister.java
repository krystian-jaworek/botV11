package com.tradingbot.backtest.permutation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingbot.persistence.entities.SimulationResultDocument;
import com.tradingbot.persistence.repositories.DynamicSimulationResultRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Batches simulation results and persists them to MongoDB periodically.
 *
 * Strategy:
 * - Collects results in memory
 * - Flushes to MongoDB when:
 *   1. Batch size reaches threshold (e.g., 1000 results)
 *   2. Time interval elapses (e.g., 10 seconds)
 *   3. Explicitly flushed
 *
 * Thread-safe for concurrent use.
 */
@Slf4j
public class BatchResultsPersister implements AutoCloseable {

    private final DynamicSimulationResultRepository repository;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final long flushIntervalSeconds;

    private final List<SimulationResultDocument> buffer;
    private final ReentrantLock lock;
    private final ScheduledExecutorService scheduler;

    private volatile boolean closed = false;
    private long totalPersisted = 0;

    public BatchResultsPersister(
        DynamicSimulationResultRepository repository,
        int batchSize,
        long flushIntervalSeconds
    ) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
        this.batchSize = batchSize;
        this.flushIntervalSeconds = flushIntervalSeconds;

        this.buffer = new ArrayList<>();
        this.lock = new ReentrantLock();

        // Start periodic flush scheduler
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "batch-persister-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
            this::periodicFlush,
            flushIntervalSeconds,
            flushIntervalSeconds,
            TimeUnit.SECONDS
        );

        log.info("BatchResultsPersister initialized (batchSize={}, flushInterval={}s)",
            batchSize, flushIntervalSeconds);
    }

    public BatchResultsPersister(DynamicSimulationResultRepository repository) {
        this(repository, 1000, 10);
    }

    /**
     * Add a result to the batch.
     * Will automatically flush if batch size is reached.
     */
    public void persist(SimulationTask.Result result) {
        if (closed) {
            throw new IllegalStateException("BatchResultsPersister is closed");
        }

        if (!result.isSuccess()) {
            log.debug("Skipping failed simulation task {}", result.getTaskId());
            return;
        }

        // Convert to document
        String configJson = serializeConfig(result.getSimulationResult().getAlgorithmName());
        SimulationResultDocument document = SimulationResultDocument.fromSimulationResult(
            result.getSimulationResult(),
            configJson
        );

        // Add to buffer
        boolean shouldFlush = false;
        lock.lock();
        try {
            buffer.add(document);
            if (buffer.size() >= batchSize) {
                shouldFlush = true;
            }
        } finally {
            lock.unlock();
        }

        // Flush if needed (outside lock)
        if (shouldFlush) {
            flush();
        }
    }

    /**
     * Flush all buffered results to MongoDB
     */
    public void flush() {
        List<SimulationResultDocument> toFlush;

        lock.lock();
        try {
            if (buffer.isEmpty()) {
                return;
            }

            toFlush = new ArrayList<>(buffer);
            buffer.clear();
        } finally {
            lock.unlock();
        }

        try {
            repository.saveAll(toFlush);
            totalPersisted += toFlush.size();
            log.info("Flushed {} results to MongoDB (total persisted: {})",
                toFlush.size(), totalPersisted);
        } catch (Exception e) {
            log.error("Failed to flush {} results", toFlush.size(), e);
            // Re-add to buffer on failure
            lock.lock();
            try {
                buffer.addAll(0, toFlush);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Periodic flush triggered by scheduler
     */
    private void periodicFlush() {
        try {
            flush();
        } catch (Exception e) {
            log.error("Error in periodic flush", e);
        }
    }

    /**
     * Get number of results currently buffered
     */
    public int getBufferSize() {
        lock.lock();
        try {
            return buffer.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get total number of results persisted
     */
    public long getTotalPersisted() {
        return totalPersisted;
    }

    /**
     * Serialize algorithm config to JSON (placeholder - can be enhanced)
     */
    private String serializeConfig(String algorithmName) {
        try {
            return objectMapper.writeValueAsString(
                java.util.Map.of("algorithm", algorithmName)
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize config", e);
            return "{}";
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;

        log.info("Closing BatchResultsPersister...");

        // Stop scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Final flush
        flush();

        log.info("BatchResultsPersister closed. Total persisted: {}", totalPersisted);
    }
}
