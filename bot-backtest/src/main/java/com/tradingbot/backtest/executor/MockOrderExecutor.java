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
}
