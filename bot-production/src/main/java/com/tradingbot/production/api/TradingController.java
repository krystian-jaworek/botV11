package com.tradingbot.production.api;

import com.tradingbot.production.dto.AlgorithmStatusResponse;
import com.tradingbot.production.dto.StartAlgorithmRequest;
import com.tradingbot.production.service.AlgorithmOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for trading system control.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/algorithms")
@RequiredArgsConstructor
public class TradingController {

    private final AlgorithmOrchestrator orchestrator;

    /**
     * Start a new algorithm instance.
     *
     * POST /api/v1/algorithms/start
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startAlgorithm(@RequestBody StartAlgorithmRequest request) {
        try {
            log.info("Received request to start algorithm: {} on {}",
                request.getAlgorithmName(), request.getTradingPair());

            String algorithmId = orchestrator.startAlgorithm(request);

            return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                    "success", true,
                    "algorithmId", algorithmId,
                    "message", "Algorithm started successfully"
                ));

        } catch (Exception e) {
            log.error("Failed to start algorithm", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }

    /**
     * Stop an algorithm instance.
     *
     * POST /api/v1/algorithms/{algorithmId}/stop
     */
    @PostMapping("/{algorithmId}/stop")
    public ResponseEntity<Map<String, Object>> stopAlgorithm(@PathVariable String algorithmId) {
        try {
            log.info("Received request to stop algorithm: {}", algorithmId);

            orchestrator.stopAlgorithm(algorithmId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Algorithm stopped successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to stop algorithm", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }

    /**
     * Get list of all active algorithms.
     *
     * GET /api/v1/algorithms/active
     */
    @GetMapping("/active")
    public ResponseEntity<List<AlgorithmStatusResponse>> getActiveAlgorithms() {
        try {
            List<AlgorithmStatusResponse> algorithms = orchestrator.getActiveAlgorithms();

            return ResponseEntity.ok(algorithms);

        } catch (Exception e) {
            log.error("Failed to get active algorithms", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(List.of());
        }
    }

    /**
     * Get status of a specific algorithm.
     *
     * GET /api/v1/algorithms/{algorithmId}
     */
    @GetMapping("/{algorithmId}")
    public ResponseEntity<AlgorithmStatusResponse> getAlgorithmStatus(@PathVariable String algorithmId) {
        try {
            AlgorithmStatusResponse status = orchestrator.getAlgorithmStatus(algorithmId);

            if (status == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Failed to get algorithm status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .build();
        }
    }

    /**
     * Health check endpoint.
     *
     * GET /api/v1/algorithms/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        int activeCount = orchestrator.getActiveAlgorithmCount();

        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "activeAlgorithms", activeCount,
            "timestamp", System.currentTimeMillis()
        ));
    }
}
