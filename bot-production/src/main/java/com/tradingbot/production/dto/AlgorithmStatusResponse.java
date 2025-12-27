package com.tradingbot.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Response containing status of a running algorithm.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmStatusResponse {

    private String algorithmId;
    private String algorithmName;
    private String tradingPair;
    private boolean active;

    private Instant createdAt;
    private Instant lastUpdateAt;

    /**
     * Current portfolio state
     */
    private BigDecimal currentCashBalance;
    private int openPositionsCount;
    private int closedPositionsCount;

    /**
     * Performance metrics
     */
    private BigDecimal totalRealizedPnL;
    private BigDecimal totalUnrealizedPnL;

    /**
     * Algorithm configuration
     */
    private Map<String, Object> configuration;

    /**
     * Internal state (optional, can be large)
     */
    private Map<String, Object> internalState;
}
