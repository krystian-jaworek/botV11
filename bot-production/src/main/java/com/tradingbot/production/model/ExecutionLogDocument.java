package com.tradingbot.production.model;

import com.tradingbot.core.algorithms.TradingDecision;
import com.tradingbot.core.models.Candle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for logging algorithm execution history.
 * One document per candle processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("algorithm-executions")
public class ExecutionLogDocument {

    @Id
    private String id;

    /**
     * Reference to the algorithm instance
     */
    @Indexed
    private String instanceId;

    /**
     * When this execution occurred
     */
    @Indexed
    private Instant timestamp;

    /**
     * The candle that was processed
     */
    private Candle candle;

    /**
     * The trading decision made by the algorithm
     */
    private TradingDecision decision;

    /**
     * State backup before decision was executed
     * Useful for debugging and rollback
     */
    private AlgorithmState stateBefore;

    /**
     * Filled order details (if decision resulted in an order)
     * Null if decision was Hold
     */
    private LiveFilledOrder filledOrder;

    /**
     * Error message (if execution failed)
     */
    private String error;

    /**
     * Execution duration in milliseconds
     */
    private Long executionTimeMs;
}
