package com.tradingbot.backtest.permutation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingbot.persistence.entities.SimulationResultDocument;
import com.tradingbot.persistence.repositories.DynamicSimulationResultRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final ConcurrentLinkedQueue<SimulationResultDocument> buffer;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger bufferSize;

    private volatile boolean closed = false;
    private final AtomicInteger totalPersisted;

    public BatchResultsPersister(
        DynamicSimulationResultRepository repository,
        int batchSize,
        long flushIntervalSeconds
    ) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
        this.batchSize = batchSize;
        this.flushIntervalSeconds = flushIntervalSeconds;

        this.buffer = new ConcurrentLinkedQueue<>();
        this.bufferSize = new AtomicInteger(0);
        this.totalPersisted = new AtomicInteger(0);

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

        // Convert to document with algorithm configuration
        String configJson = serializeConfig(result.getAlgorithmConfig());
        SimulationResultDocument document = SimulationResultDocument.fromSimulationResult(
            result.getSimulationResult(),
            configJson
        );

        // Add to buffer (lock-free)
        buffer.offer(document);
        int currentSize = bufferSize.incrementAndGet();

        // Flush if needed
        if (currentSize >= batchSize) {
            flush();
        }
    }

    /**
     * Flush all buffered results to MongoDB
     */
    public synchronized void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        // Drain buffer to list
        List<SimulationResultDocument> toFlush = new ArrayList<>();
        SimulationResultDocument doc;
        while ((doc = buffer.poll()) != null) {
            toFlush.add(doc);
        }

        if (toFlush.isEmpty()) {
            return;
        }

        // Reset buffer size counter
        bufferSize.addAndGet(-toFlush.size());

        try {
            repository.saveAll(toFlush);
            int persisted = totalPersisted.addAndGet(toFlush.size());
            log.info("Flushed {} results to MongoDB (total persisted: {})",
                toFlush.size(), persisted);
        } catch (Exception e) {
            log.error("Failed to flush {} results", toFlush.size(), e);
            // Re-add to buffer on failure
            toFlush.forEach(buffer::offer);
            bufferSize.addAndGet(toFlush.size());
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
        return bufferSize.get();
    }

    /**
     * Get total number of results persisted
     */
    public long getTotalPersisted() {
        return totalPersisted.get();
    }

    /**
     * Serialize algorithm config to JSON
     */
    private String serializeConfig(Object algorithmConfig) {
        if (algorithmConfig == null) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(algorithmConfig);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize config: {}", algorithmConfig.getClass().getName(), e);
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

        log.info("BatchResultsPersister closed. Total persisted: {}", totalPersisted.get());
    }
}
