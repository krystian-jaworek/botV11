package com.tradingbot.core.metrics;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Represents a single filled order (open or close) during simulation.
 * Contains all relevant information for order history tracking.
 */
@Value
@Builder
public class FilledOrder {

    /**
     * Type of order execution
     */
    public enum OrderType {
        OPEN,   // Position opened
        CLOSE   // Position closed
    }

    /**
     * Timestamp when order was filled
     */
    long timestamp;

    /**
     * Type of order (OPEN or CLOSE)
     */
    OrderType type;

    /**
     * Position ID associated with this order
     */
    String positionId;

    /**
     * Execution price
     * For OPEN: entry price
     * For CLOSE: close/exit price
     */
    BigDecimal price;

    /**
     * Average entry price for the position
     * For OPEN: same as price
     * For CLOSE: original entry price of the position
     */
    BigDecimal avgEntry;

    /**
     * Quantity/volume of the order
     */
    BigDecimal quantity;

    /**
     * Realized P&L for CLOSE orders
     * Null for OPEN orders
     */
    BigDecimal realizedPnL;

    @Override
    public String toString() {
        if (type == OrderType.OPEN) {
            return String.format(
                "FilledOrder[OPEN | Time: %d | ID: %s | Price: %.2f | Qty: %.8f]",
                timestamp, positionId, price, quantity
            );
        } else {
            return String.format(
                "FilledOrder[CLOSE | Time: %d | ID: %s | Entry: %.2f | Close: %.2f | Qty: %.8f | P&L: %.2f]",
                timestamp, positionId, avgEntry, price, quantity, realizedPnL
            );
        }
    }
}
