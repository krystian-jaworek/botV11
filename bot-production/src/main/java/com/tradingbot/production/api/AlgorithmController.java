package com.tradingbot.production.api;

import com.tradingbot.production.dto.CreateAlgorithmRequest;
import com.tradingbot.production.dto.CreateAlgorithmResponse;
import com.tradingbot.production.exchange.ByBitFuturesClient;
import com.tradingbot.production.model.*;
import com.tradingbot.production.repository.AlgorithmInstanceRepository;
import com.tradingbot.production.repository.ExecutionLogRepository;
import com.tradingbot.production.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST API for managing live trading algorithm instances.
 * All endpoints require X-API-Key header for authentication.
 */
@Slf4j
@RestController
@RequestMapping("/api/algorithms")
@RequiredArgsConstructor
public class AlgorithmController {

    private final AlgorithmInstanceRepository instanceRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final ByBitFuturesClient bybitClient;
    private final EncryptionService encryptionService;

    /**
     * Create new algorithm instance
     * POST /api/algorithms
     */
    @PostMapping
    public ResponseEntity<?> createInstance(@RequestBody CreateAlgorithmRequest request) {
        try {
            // Validate unique name
            if (instanceRepository.existsByName(request.getName())) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Instance with name '" + request.getName() + "' already exists"));
            }

            // Fetch initial balance from ByBit
            BigDecimal initialBalance = bybitClient.getAccountBalance(
                request.getBybitApiKey(),
                request.getBybitApiSecret()
            );

            log.info("Creating algorithm instance '{}' with initial balance: ${}", request.getName(), initialBalance);

            // Encrypt ByBit secret
            String encryptedSecret = encryptionService.encrypt(request.getBybitApiSecret());

            // Create initial state
            AlgorithmState initialState = AlgorithmState.createInitialState(initialBalance);

            // Create instance document
            AlgorithmInstanceDocument instance = AlgorithmInstanceDocument.builder()
                .name(request.getName())
                .type(request.getType())
                .tradingPair(request.getTradingPair())
                .bybitApiKey(request.getBybitApiKey())
                .bybitApiSecretEncrypted(encryptedSecret)
                .config(request.getConfig())
                .state(initialState)
                .status(AlgorithmStatus.STOPPED)
                .createdAt(Instant.now())
                .consecutiveErrors(0)
                .build();

            instance = instanceRepository.save(instance);

            log.info("Algorithm instance created: {} (ID: {})", instance.getName(), instance.getId());

            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateAlgorithmResponse(instance.getId(), initialBalance));

        } catch (Exception e) {
            log.error("Failed to create algorithm instance", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * List all algorithm instances
     * GET /api/algorithms
     */
    @GetMapping
    public ResponseEntity<List<AlgorithmInstanceDocument>> listInstances() {
        List<AlgorithmInstanceDocument> instances = instanceRepository.findAll();
        return ResponseEntity.ok(instances);
    }

    /**
     * Get specific algorithm instance
     * GET /api/algorithms/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getInstance(@PathVariable String id) {
        return instanceRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Start algorithm instance
     * POST /api/algorithms/{id}/start
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<?> startInstance(@PathVariable String id) {
        try {
            AlgorithmInstanceDocument instance = instanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instance not found"));

            if (instance.getStatus() == AlgorithmStatus.RUNNING) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Instance is already running"));
            }

            instance.setStatus(AlgorithmStatus.RUNNING);
            instance.setStartedAt(Instant.now());
            instance.resetErrors();
            instanceRepository.save(instance);

            log.info("Algorithm instance started: {}", instance.getName());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Algorithm started",
                "instanceId", instance.getId()
            ));

        } catch (Exception e) {
            log.error("Failed to start instance", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Stop algorithm instance
     * POST /api/algorithms/{id}/stop
     */
    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stopInstance(@PathVariable String id) {
        try {
            AlgorithmInstanceDocument instance = instanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instance not found"));

            instance.setStatus(AlgorithmStatus.STOPPED);
            instanceRepository.save(instance);

            log.info("Algorithm instance stopped: {}", instance.getName());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Algorithm stopped",
                "instanceId", instance.getId()
            ));

        } catch (Exception e) {
            log.error("Failed to stop instance", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get algorithm state
     * GET /api/algorithms/{id}/state
     */
    @GetMapping("/{id}/state")
    public ResponseEntity<?> getState(@PathVariable String id) {
        return instanceRepository.findById(id)
            .map(instance -> ResponseEntity.ok(instance.getState()))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get execution logs
     * GET /api/algorithms/{id}/logs?limit=100
     */
    @GetMapping("/{id}/logs")
    public ResponseEntity<List<ExecutionLogDocument>> getLogs(
        @PathVariable String id,
        @RequestParam(defaultValue = "100") int limit
    ) {
        List<ExecutionLogDocument> logs = executionLogRepository
            .findByInstanceIdOrderByTimestampDesc(id, PageRequest.of(0, limit));

        return ResponseEntity.ok(logs);
    }

    /**
     * Delete algorithm instance
     * DELETE /api/algorithms/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInstance(@PathVariable String id) {
        try {
            AlgorithmInstanceDocument instance = instanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instance not found"));

            if (instance.getStatus() == AlgorithmStatus.RUNNING) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Cannot delete running instance. Stop it first."));
            }

            instanceRepository.deleteById(id);

            log.info("Algorithm instance deleted: {}", instance.getName());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Instance deleted"
            ));

        } catch (Exception e) {
            log.error("Failed to delete instance", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Health check
     * GET /api/algorithms/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        long runningCount = instanceRepository.countByStatus(AlgorithmStatus.RUNNING);
        long stoppedCount = instanceRepository.countByStatus(AlgorithmStatus.STOPPED);
        long errorCount = instanceRepository.countByStatus(AlgorithmStatus.ERROR);

        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "running", runningCount,
            "stopped", stoppedCount,
            "error", errorCount,
            "timestamp", Instant.now().toEpochMilli()
        ));
    }
}
