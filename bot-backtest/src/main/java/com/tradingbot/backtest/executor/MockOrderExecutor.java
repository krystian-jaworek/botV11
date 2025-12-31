package com.tradingbot.backtest.executor;

import com.tradingbot.core.algorithms.OrderExecutor;
import com.tradingbot.core.models.ClosedPosition;
import com.tradingbot.core.models.OrderSide;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.core.models.Position;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Mock order executor for backtesting.
 * Executes orders instantly without slippage, fees, or latency.
 */
@Slf4j
public class MockOrderExecutor implements OrderExecutor {

    private final Portfolio portfolio;

    public MockOrderExecutor(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    @Override
    public Position openPosition(OrderSide side, BigDecimal quantity, BigDecimal price,
                                 long timestamp, String metadata) {

        Position position = Position.createWithMetadata(side, price, quantity, timestamp, metadata);

        // Add to portfolio (this will deduct cash)
        portfolio.addPosition(position);

        log.debug("Opened position: {}", position);

        return position;
    }

    /**
     * Increase existing position (futures-style DCA).
     * Adds to position quantity and recalculates weighted average entry.
     */
    public Position increasePosition(String positionId, BigDecimal additionalQuantity, BigDecimal price) {
        // Increase position in portfolio (this will deduct cash and update position)
        Position updatedPosition = portfolio.increasePosition(positionId, additionalQuantity, price);

        log.debug("Increased position: {} +{} @ {} -> total={}, new avgEntry={}",
            positionId.substring(0, 8),
            additionalQuantity,
            price,
            updatedPosition.getQuantity(),
            updatedPosition.getEntryPrice()
        );

        return updatedPosition;
    }

    @Override
    public ClosedPosition closePosition(Position position, BigDecimal closePrice, long timestamp) {

        // Close position in portfolio (this will add cash from proceeds)
        ClosedPosition closedPosition = portfolio.closePosition(position.getId(), closePrice, timestamp);

        log.debug("Closed position: {} with PnL: {} ({}%)",
            closedPosition.getId().substring(0, 8),
            closedPosition.getRealizedPnL(),
            closedPosition.getRealizedPnLPercentage()
        );

        return closedPosition;
    }

    @Override
    public ClosedPosition closePartialPosition(
        Position position,
        BigDecimal quantityToClose,
        BigDecimal closePrice,
        long timestamp
    ) {
        // Close partial position in portfolio
        ClosedPosition closedPosition = portfolio.closePartialPosition(
            position.getId(),
            quantityToClose,
            closePrice,
            timestamp
        );

        log.debug("Partially closed position: {} - closed {}/{} with PnL: {} ({}%), remaining: {}",
            closedPosition.getId().substring(0, 8),
            quantityToClose,
            position.getQuantity(),
            closedPosition.getRealizedPnL(),
            closedPosition.getRealizedPnLPercentage(),
            closedPosition.getRemainingQuantity()
        );

        return closedPosition;
    }
}
