package com.tradingbot.production.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingbot.algorithms.gridbot.GridBotAlgorithm;
import com.tradingbot.algorithms.gridbot.GridBotConfig;
import com.tradingbot.core.algorithms.AlgorithmState;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.persistence.entities.AlgorithmStateDocument;
import com.tradingbot.persistence.repositories.AlgorithmStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages persistence and recovery of algorithm state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlgorithmStateManager {

    private final AlgorithmStateRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Save algorithm instance state to MongoDB
     */
    public void saveState(AlgorithmInstance instance) {
        try {
            AlgorithmState algorithmState = instance.getAlgorithm().getState();
            algorithmState.setTradingPair(instance.getTradingPair());
            algorithmState.setLastUpdateTimestamp(System.currentTimeMillis());

            // Create or update document
            AlgorithmStateDocument document = repository.findByAlgorithmId(instance.getAlgorithmId())
                .map(existing -> {
                    existing.updateFromAlgorithmState(algorithmState);
                    existing.setActive(instance.isActive());
                    return existing;
                })
                .orElseGet(() -> AlgorithmStateDocument.fromAlgorithmState(
                    instance.getAlgorithmId(),
                    algorithmState,
                    instance.getConfigJson(),
                    instance.isActive()
                ));

            repository.save(document);

            log.debug("Saved state for algorithm: {}", instance.getAlgorithmId());

        } catch (Exception e) {
            log.error("Failed to save state for algorithm: {}", instance.getAlgorithmId(), e);
        }
    }

    /**
     * Recover all active algorithms from MongoDB
     */
    public List<AlgorithmInstance> recoverActiveAlgorithms() {
        List<AlgorithmInstance> recovered = new ArrayList<>();

        try {
            List<AlgorithmStateDocument> activeStates = repository.findByActiveTrue();

            log.info("Found {} active algorithm states in database", activeStates.size());

            for (AlgorithmStateDocument document : activeStates) {
                try {
                    AlgorithmInstance instance = recoverInstance(document);
                    recovered.add(instance);

                    log.info("Recovered algorithm: {} ({})",
                        instance.getAlgorithmId(),
                        instance.getAlgorithm().getName());

                } catch (Exception e) {
                    log.error("Failed to recover algorithm: {}", document.getAlgorithmId(), e);
                }
            }

        } catch (Exception e) {
            log.error("Failed to recover active algorithms", e);
        }

        return recovered;
    }

    /**
     * Recover a single algorithm instance from document
     */
    private AlgorithmInstance recoverInstance(AlgorithmStateDocument document) throws Exception {
        // Parse configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> config = objectMapper.readValue(document.getConfigJson(), Map.class);

        // Create algorithm based on name
        var algorithm = createAlgorithm(document.getAlgorithmName(), config);

        // Restore algorithm state
        AlgorithmState state = document.toAlgorithmState();
        algorithm.restoreState(state);

        // Create portfolio (simplified - would need to restore positions in full implementation)
        Portfolio portfolio = new Portfolio(new BigDecimal("10000")); // Placeholder

        return AlgorithmInstance.builder()
            .algorithmId(document.getAlgorithmId())
            .algorithm(algorithm)
            .portfolio(portfolio)
            .tradingPair(document.getTradingPair())
            .configJson(document.getConfigJson())
            .createdAt(document.getCreatedAt())
            .lastUpdateAt(document.getLastUpdateAt())
            .active(document.isActive())
            .build();
    }

    /**
     * Create algorithm from name and config
     */
    private com.tradingbot.core.algorithms.TradingAlgorithm<?> createAlgorithm(
        String algorithmName,
        Map<String, Object> configMap
    ) throws Exception {

        switch (algorithmName.toLowerCase()) {
            case "gridbot":
                GridBotConfig config = GridBotConfig.builder()
                    .gridLevels(((Number) configMap.get("gridLevels")).intValue())
                    .gridDistancePercent(new BigDecimal(configMap.get("gridDistancePercent").toString()))
                    .takeProfitPercent(new BigDecimal(configMap.get("takeProfitPercent").toString()))
                    .portfolioAllocationPercent(new BigDecimal(configMap.get("portfolioAllocationPercent").toString()))
                    .build();
                return new GridBotAlgorithm(config);

            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        }
    }

    /**
     * Delete inactive algorithms older than specified timestamp
     */
    public void cleanupOldInactiveAlgorithms(long olderThanTimestamp) {
        repository.deleteByActiveFalseAndLastUpdateTimestampLessThan(olderThanTimestamp);
        log.info("Cleaned up inactive algorithms older than {}", olderThanTimestamp);
    }
}
