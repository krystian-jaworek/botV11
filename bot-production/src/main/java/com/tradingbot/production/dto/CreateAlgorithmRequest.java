package com.tradingbot.production.dto;

import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticConfig;
import com.tradingbot.core.models.TradingPair;
import com.tradingbot.production.model.AlgorithmType;
import lombok.Data;

/**
 * Request to create a new algorithm instance
 */
@Data
public class CreateAlgorithmRequest {
    private String name;
    private AlgorithmType type;
    private TradingPair tradingPair;
    private String bybitApiKey;
    private String bybitApiSecret;
    private SMAOpportunisticConfig config;
}
