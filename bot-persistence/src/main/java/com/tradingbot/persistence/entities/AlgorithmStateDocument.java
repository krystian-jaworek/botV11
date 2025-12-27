package com.tradingbot.persistence.entities;

import com.tradingbot.core.algorithms.AlgorithmState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB document for persisting algorithm state (production system).
 * Used for recovery after restart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "algorithm_states")
@CompoundIndexes({
    @CompoundIndex(name = "algo_pair_idx", def = "{'algorithmId': 1, 'active': -1}", unique = true)
})
public class AlgorithmStateDocument {

    @Id
    private String id;

    /**
     * Unique identifier for this algorithm instance
     * Format: <AlgorithmName>-<TradingPair>-<ConfigHash>
     */
    @Indexed(unique = true)
    private String algorithmId;

    @Indexed
    private String algorithmName;

    @Indexed
    private String tradingPair;

    private String configJson;

    @Indexed
    private boolean active;

    private Instant createdAt;
    private Instant lastUpdateAt;
    private long lastUpdateTimestamp;

    /**
     * Generic state data
     */
    @Builder.Default
    private Map<String, Object> stateData = new HashMap<>();

    /**
     * Convert from core AlgorithmState
     */
    public static AlgorithmStateDocument fromAlgorithmState(
        String algorithmId,
        AlgorithmState state,
        String configJson,
        boolean active
    ) {
        return AlgorithmStateDocument.builder()
            .algorithmId(algorithmId)
            .algorithmName(state.getAlgorithmName())
            .tradingPair(state.getTradingPair())
            .configJson(configJson)
            .active(active)
            .createdAt(Instant.now())
            .lastUpdateAt(Instant.now())
            .lastUpdateTimestamp(state.getLastUpdateTimestamp())
            .stateData(state.getStateData())
            .build();
    }

    /**
     * Convert to core AlgorithmState
     */
    public AlgorithmState toAlgorithmState() {
        return AlgorithmState.builder()
            .algorithmName(algorithmName)
            .tradingPair(tradingPair)
            .lastUpdateTimestamp(lastUpdateTimestamp)
            .stateData(new HashMap<>(stateData))
            .build();
    }

    /**
     * Update from core AlgorithmState
     */
    public void updateFromAlgorithmState(AlgorithmState state) {
        this.lastUpdateAt = Instant.now();
        this.lastUpdateTimestamp = state.getLastUpdateTimestamp();
        this.stateData = new HashMap<>(state.getStateData());
    }
}
