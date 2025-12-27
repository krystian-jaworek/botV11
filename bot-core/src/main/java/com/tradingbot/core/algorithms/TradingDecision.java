package com.tradingbot.core.algorithms;

import com.tradingbot.core.models.OrderSide;

import java.math.BigDecimal;

/**
 * Sealed interface representing a trading decision made by an algorithm.
 * Uses Java 21 sealed types for exhaustive pattern matching.
 */
public sealed interface TradingDecision {

    /**
     * Decision to open a new position
     */
    record OpenPosition(
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        String metadata  // Optional metadata (e.g., grid level)
    ) implements TradingDecision {
        public OpenPosition(OrderSide side, BigDecimal quantity, BigDecimal price) {
            this(side, quantity, price, null);
        }
    }

    /**
     * Decision to close an existing position
     */
    record ClosePosition(
        String positionId,
        BigDecimal price
    ) implements TradingDecision {}

    /**
     * Decision to do nothing (hold current positions)
     */
    record Hold() implements TradingDecision {
        // Singleton instance for efficiency
        public static final Hold INSTANCE = new Hold();
    }
}
