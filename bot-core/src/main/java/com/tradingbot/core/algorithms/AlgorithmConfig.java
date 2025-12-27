package com.tradingbot.core.algorithms;

/**
 * Marker interface for algorithm configurations.
 * Each algorithm should define its own config class implementing this interface.
 */
public interface AlgorithmConfig {
    /**
     * Get a unique identifier for this configuration
     */
    String getConfigId();
}
