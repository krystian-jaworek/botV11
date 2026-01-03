package com.tradingbot.production.model;

import com.tradingbot.core.models.OrderSide;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Filled order from live trading (ByBit exchange).
 * Different from backtest FilledOrder - contains exchange-specific data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveFilledOrder {
    /**
     * Order ID from exchange
     */
    private String orderId;

    /**
     * Trading pair (e.g., "BTCUSDT")
     */
    private String tradingPair;

    /**
     * Order side (BUY/SELL)
     */
    private OrderSide side;

    /**
     * Filled quantity
     */
    private BigDecimal quantity;

    /**
     * Filled price (average if partial fills)
     */
    private BigDecimal price;

    /**
     * Timestamp when order was filled
     */
    private Long timestamp;

    /**
     * Fee paid for this order
     */
    private BigDecimal fee;
}
