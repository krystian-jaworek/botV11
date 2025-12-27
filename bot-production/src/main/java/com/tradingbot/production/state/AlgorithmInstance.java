package com.tradingbot.production.state;

import com.tradingbot.core.algorithms.TradingAlgorithm;
import com.tradingbot.core.models.Portfolio;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Represents a running algorithm instance in production.
 */
@Data
@Builder
public class AlgorithmInstance {
    private String algorithmId;
    private TradingAlgorithm<?> algorithm;
    private Portfolio portfolio;
    private String tradingPair;
    private String configJson;

    private Instant createdAt;
    private Instant lastUpdateAt;
    private boolean active;
}
