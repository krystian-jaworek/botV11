package com.tradingbot.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request to start a new trading algorithm instance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartAlgorithmRequest {

    /**
     * Algorithm name (e.g., "GridBot")
     */
    private String algorithmName;

    /**
     * Trading pair (e.g., "BTCUSDT")
     */
    private String tradingPair;

    /**
     * Initial capital allocated to this algorithm
     */
    private BigDecimal initialCapital;

    /**
     * Algorithm-specific configuration parameters.
     * For GridBot:
     * - gridLevels (Integer)
     * - gridDistancePercent (Double)
     * - takeProfitPercent (Double)
     * - portfolioAllocationPercent (Double)
     */
    private Map<String, Object> parameters;
}
