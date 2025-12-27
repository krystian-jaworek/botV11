package com.tradingbot.production.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Event sourcing logger for debugging.
 * Logs every algorithm decision to MongoDB when debug mode is enabled.
 *
 * Enable in application.yml:
 * debug:
 *   event-sourcing:
 *     enabled: true
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "debug.event-sourcing.enabled", havingValue = "true")
public class EventSourcingLogger {

    private final MongoTemplate mongoTemplate;

    @Value("${debug.event-sourcing.collection-name:algorithm-events}")
    private String collectionName;

    /**
     * Log an order placement event
     */
    public void logOrderPlaced(String algorithmId, String tradingPair, String side,
                               String quantity, String price, Map<String, Object> context) {
        Map<String, Object> event = new HashMap<>();
        event.put("timestamp", Instant.now());
        event.put("eventType", "ORDER_PLACED");
        event.put("algorithmId", algorithmId);
        event.put("tradingPair", tradingPair);
        event.put("side", side);
        event.put("quantity", quantity);
        event.put("price", price);
        event.put("context", context);

        saveEvent(event);

        log.debug("Event logged: ORDER_PLACED for {}", algorithmId);
    }

    /**
     * Log an order execution event
     */
    public void logOrderExecuted(String algorithmId, String positionId, String tradingPair,
                                 String side, String quantity, String executionPrice,
                                 Map<String, Object> context) {
        Map<String, Object> event = new HashMap<>();
        event.put("timestamp", Instant.now());
        event.put("eventType", "ORDER_EXECUTED");
        event.put("algorithmId", algorithmId);
        event.put("positionId", positionId);
        event.put("tradingPair", tradingPair);
        event.put("side", side);
        event.put("quantity", quantity);
        event.put("executionPrice", executionPrice);
        event.put("context", context);

        saveEvent(event);

        log.debug("Event logged: ORDER_EXECUTED for {}", algorithmId);
    }

    /**
     * Save event to MongoDB
     */
    private void saveEvent(Map<String, Object> event) {
        try {
            mongoTemplate.insert(event, collectionName);
        } catch (Exception e) {
            log.error("Failed to save event to MongoDB", e);
        }
    }
}
