package com.tradingbot.production.model;

import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticConfig;
import com.tradingbot.core.models.TradingPair;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for storing algorithm instances.
 * Represents a live trading algorithm configuration and runtime state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("algorithm-instances")
public class AlgorithmInstanceDocument {

    @Id
    private String id;

    /**
     * User-friendly name for the instance (e.g., "SMA-BTC-Main")
     */
    @Indexed
    private String name;

    /**
     * Type of algorithm (currently only SMA_OPPORTUNISTIC)
     */
    @Indexed
    private AlgorithmType type;

    /**
     * Trading pair (BTCUSDT, ETHUSDT, etc.)
     */
    private TradingPair tradingPair;

    // ByBit Futures API Credentials
    /**
     * ByBit API key (stored in plain text as it's not sensitive)
     */
    private String bybitApiKey;

    /**
     * ByBit API secret (encrypted with AES)
     */
    private String bybitApiSecretEncrypted;

    // Configuration
    /**
     * Algorithm configuration (SMAOpportunisticConfig)
     * MongoDB will serialize this as embedded document
     */
    private SMAOpportunisticConfig config;

    // Runtime State
    /**
     * Current algorithm state (captured after each candle)
     * Embedded document in MongoDB
     */
    private AlgorithmState state;

    // Status
    /**
     * Current status of the algorithm
     */
    @Indexed
    private AlgorithmStatus status;

    // Timestamps
    private Instant createdAt;
    private Instant startedAt;
    private Instant lastExecutedAt;

    /**
     * Timestamp of the last processed candle
     * Used to avoid reprocessing the same candle
     */
    private Long lastCandleTimestamp;

    // Error tracking
    private String lastError;
    private Integer consecutiveErrors;

    /**
     * Reset error counter (called after successful execution)
     */
    public void resetErrors() {
        this.consecutiveErrors = 0;
        this.lastError = null;
    }

    /**
     * Increment error counter and update last error
     */
    public void recordError(String error) {
        this.consecutiveErrors = (this.consecutiveErrors == null ? 0 : this.consecutiveErrors) + 1;
        this.lastError = error;
    }

    /**
     * Check if algorithm should be stopped due to too many errors
     */
    public boolean shouldStopDueToErrors() {
        return consecutiveErrors != null && consecutiveErrors >= 3;
    }
}
