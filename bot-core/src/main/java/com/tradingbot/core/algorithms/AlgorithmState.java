package com.tradingbot.core.algorithms;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic container for algorithm state.
 * Used for serialization/deserialization and recovery after restart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmState {
    /**
     * Algorithm name
     */
    private String algorithmName;

    /**
     * Trading pair (e.g., "BTCUSDT")
     */
    private String tradingPair;

    /**
     * Timestamp of last update
     */
    private long lastUpdateTimestamp;

    /**
     * Generic state data - each algorithm can store whatever it needs
     */
    @Builder.Default
    private Map<String, Object> stateData = new HashMap<>();

    /**
     * Helper method to store a value in state
     */
    public void putState(String key, Object value) {
        stateData.put(key, value);
    }

    /**
     * Helper method to retrieve a value from state
     */
    @SuppressWarnings("unchecked")
    public <T> T getState(String key, Class<T> type) {
        return (T) stateData.get(key);
    }

    /**
     * Helper method to retrieve a value with default
     */
    @SuppressWarnings("unchecked")
    public <T> T getStateOrDefault(String key, T defaultValue) {
        Object value = stateData.get(key);
        return value != null ? (T) value : defaultValue;
    }
}
