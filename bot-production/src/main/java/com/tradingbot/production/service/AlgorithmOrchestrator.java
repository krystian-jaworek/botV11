package com.tradingbot.production.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingbot.algorithms.gridbot.GridBotAlgorithm;
import com.tradingbot.algorithms.gridbot.GridBotConfig;
import com.tradingbot.core.algorithms.TradingAlgorithm;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.production.dto.AlgorithmStatusResponse;
import com.tradingbot.production.dto.StartAlgorithmRequest;
import com.tradingbot.production.state.AlgorithmInstance;
import com.tradingbot.production.state.AlgorithmStateManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Orchestrates algorithm lifecycle: start, stop, recovery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlgorithmOrchestrator {

    private final AlgorithmStateManager stateManager;
    private final ObjectMapper objectMapper;

    private final Map<String, AlgorithmInstance> activeAlgorithms = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("Initializing AlgorithmOrchestrator...");

        // Recover active algorithms from database
        List<AlgorithmInstance> recovered = stateManager.recoverActiveAlgorithms();

        for (AlgorithmInstance instance : recovered) {
            activeAlgorithms.put(instance.getAlgorithmId(), instance);
            log.info("Recovered algorithm: {}", instance.getAlgorithmId());
        }

        log.info("AlgorithmOrchestrator initialized with {} active algorithms", activeAlgorithms.size());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down AlgorithmOrchestrator...");

        // Save state of all active algorithms
        for (AlgorithmInstance instance : activeAlgorithms.values()) {
            try {
                stateManager.saveState(instance);
            } catch (Exception e) {
                log.error("Failed to save state for algorithm {}", instance.getAlgorithmId(), e);
            }
        }

        log.info("AlgorithmOrchestrator shutdown complete");
    }

    /**
     * Start a new algorithm instance
     */
    public String startAlgorithm(StartAlgorithmRequest request) throws Exception {
        // Create algorithm based on request
        TradingAlgorithm<?> algorithm = createAlgorithm(request);

        // Create portfolio
        Portfolio portfolio = new Portfolio(request.getInitialCapital());

        // Generate algorithm ID
        String algorithmId = generateAlgorithmId(
            request.getAlgorithmName(),
            request.getTradingPair(),
            request.getParameters()
        );

        // Check if already exists
        if (activeAlgorithms.containsKey(algorithmId)) {
            throw new IllegalStateException("Algorithm already running: " + algorithmId);
        }

        // Create instance
        AlgorithmInstance instance = AlgorithmInstance.builder()
            .algorithmId(algorithmId)
            .algorithm(algorithm)
            .portfolio(portfolio)
            .tradingPair(request.getTradingPair())
            .configJson(objectMapper.writeValueAsString(request.getParameters()))
            .createdAt(java.time.Instant.now())
            .lastUpdateAt(java.time.Instant.now())
            .active(true)
            .build();

        // Save to state manager
        stateManager.saveState(instance);

        // Add to active algorithms
        activeAlgorithms.put(algorithmId, instance);

        log.info("Started algorithm: {}", algorithmId);

        return algorithmId;
    }

    /**
     * Stop an algorithm instance
     */
    public void stopAlgorithm(String algorithmId) {
        AlgorithmInstance instance = activeAlgorithms.remove(algorithmId);

        if (instance == null) {
            throw new IllegalArgumentException("Algorithm not found: " + algorithmId);
        }

        // Mark as inactive
        instance.setActive(false);

        // Save final state
        stateManager.saveState(instance);

        log.info("Stopped algorithm: {}", algorithmId);
    }

    /**
     * Get all active algorithms
     */
    public List<AlgorithmStatusResponse> getActiveAlgorithms() {
        return activeAlgorithms.values().stream()
            .map(this::toStatusResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get status of specific algorithm
     */
    public AlgorithmStatusResponse getAlgorithmStatus(String algorithmId) {
        AlgorithmInstance instance = activeAlgorithms.get(algorithmId);

        if (instance == null) {
            return null;
        }

        return toStatusResponse(instance);
    }

    /**
     * Get active algorithm count
     */
    public int getActiveAlgorithmCount() {
        return activeAlgorithms.size();
    }

    /**
     * Get algorithm instance (for scheduler)
     */
    public AlgorithmInstance getInstance(String algorithmId) {
        return activeAlgorithms.get(algorithmId);
    }

    /**
     * Get all active instances (for scheduler)
     */
    public List<AlgorithmInstance> getAllActiveInstances() {
        return List.copyOf(activeAlgorithms.values());
    }

    /**
     * Create algorithm from request
     */
    private TradingAlgorithm<?> createAlgorithm(StartAlgorithmRequest request) throws Exception {
        switch (request.getAlgorithmName().toLowerCase()) {
            case "gridbot":
                return createGridBot(request.getParameters());
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + request.getAlgorithmName());
        }
    }

    /**
     * Create GridBot from parameters
     */
    private GridBotAlgorithm createGridBot(Map<String, Object> params) {
        GridBotConfig config = GridBotConfig.builder()
            .gridLevels(((Number) params.get("gridLevels")).intValue())
            .gridDistancePercent(new BigDecimal(params.get("gridDistancePercent").toString()))
            .takeProfitPercent(new BigDecimal(params.get("takeProfitPercent").toString()))
            .portfolioAllocationPercent(new BigDecimal(params.get("portfolioAllocationPercent").toString()))
            .build();

        return new GridBotAlgorithm(config);
    }

    /**
     * Generate unique algorithm ID
     */
    private String generateAlgorithmId(String algorithmName, String tradingPair, Map<String, Object> params) {
        // Simple ID: AlgorithmName-TradingPair-timestamp
        return String.format("%s-%s-%d", algorithmName, tradingPair, System.currentTimeMillis());
    }

    /**
     * Convert instance to status response
     */
    private AlgorithmStatusResponse toStatusResponse(AlgorithmInstance instance) {
        return AlgorithmStatusResponse.builder()
            .algorithmId(instance.getAlgorithmId())
            .algorithmName(instance.getAlgorithm().getName())
            .tradingPair(instance.getTradingPair())
            .active(instance.isActive())
            .createdAt(instance.getCreatedAt())
            .lastUpdateAt(instance.getLastUpdateAt())
            .currentCashBalance(instance.getPortfolio().getCashBalance())
            .openPositionsCount(instance.getPortfolio().getOpenPositionCount())
            .closedPositionsCount(instance.getPortfolio().getClosedPositionCount())
            .totalRealizedPnL(instance.getPortfolio().getTotalRealizedPnL())
            .totalUnrealizedPnL(BigDecimal.ZERO) // Would need current price
            .configuration(parseConfig(instance.getConfigJson()))
            .build();
    }

    private Map<String, Object> parseConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
