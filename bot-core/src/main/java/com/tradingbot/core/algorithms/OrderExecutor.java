package com.tradingbot.core.algorithms;

import com.tradingbot.core.models.ClosedPosition;
import com.tradingbot.core.models.OrderSide;
import com.tradingbot.core.models.Position;

import java.math.BigDecimal;

/**
 * Interface for executing trading orders.
 * Implementations differ between backtesting (mock) and production (real exchange API).
 */
public interface OrderExecutor {

    /**
     * Open a new position.
     *
     * @param side Position side (LONG/SHORT)
     * @param quantity Amount to trade
     * @param price Execution price
     * @param timestamp Execution timestamp
     * @param metadata Optional metadata (e.g., grid level)
     * @return Opened position
     */
    Position openPosition(OrderSide side, BigDecimal quantity, BigDecimal price,
                         long timestamp, String metadata);

    /**
     * Open a position without metadata
     */
    default Position openPosition(OrderSide side, BigDecimal quantity, BigDecimal price, long timestamp) {
        return openPosition(side, quantity, price, timestamp, null);
    }

    /**
     * Close an existing position.
     *
     * @param position Position to close
     * @param closePrice Execution price
     * @param timestamp Execution timestamp
     * @return Closed position with realized PnL
     */
    ClosedPosition closePosition(Position position, BigDecimal closePrice, long timestamp);
}
