package com.tradingbot.core.models;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable representation of an open trading position.
 * Thread-safe for use in parallel simulations.
 */
@Value
@Builder
public class Position {
    String id;
    OrderSide side;
    BigDecimal entryPrice;
    BigDecimal quantity;
    long openTimestamp;

    /**
     * Optional metadata - can be used by algorithms to store grid level, etc.
     */
    String metadata;

    /**
     * Create a new position with auto-generated ID
     */
    public static Position create(OrderSide side, BigDecimal entryPrice, BigDecimal quantity, long timestamp) {
        return Position.builder()
            .id(UUID.randomUUID().toString())
            .side(side)
            .entryPrice(entryPrice)
            .quantity(quantity)
            .openTimestamp(timestamp)
            .build();
    }

    /**
     * Create a position with metadata (e.g., grid level)
     */
    public static Position createWithMetadata(OrderSide side, BigDecimal entryPrice,
                                              BigDecimal quantity, long timestamp, String metadata) {
        return Position.builder()
            .id(UUID.randomUUID().toString())
            .side(side)
            .entryPrice(entryPrice)
            .quantity(quantity)
            .openTimestamp(timestamp)
            .metadata(metadata)
            .build();
    }

    /**
     * Calculate current value of the position at given price.
     * For LONG: value = quantity * currentPrice
     * For SHORT: value = quantity * (2 * entryPrice - currentPrice)
     */
    public BigDecimal getCurrentValue(BigDecimal currentPrice) {
        if (side == OrderSide.LONG) {
            return quantity.multiply(currentPrice);
        } else {
            // SHORT: if entry at 100, quantity 1, price now 90 -> value = 1 * (2*100 - 90) = 110
            return quantity.multiply(
                entryPrice.multiply(BigDecimal.valueOf(2)).subtract(currentPrice)
            );
        }
    }

    /**
     * Calculate unrealized profit/loss at given price.
     * For LONG: PnL = quantity * (currentPrice - entryPrice)
     * For SHORT: PnL = quantity * (entryPrice - currentPrice)
     */
    public BigDecimal getUnrealizedPnL(BigDecimal currentPrice) {
        if (side == OrderSide.LONG) {
            return quantity.multiply(currentPrice.subtract(entryPrice));
        } else {
            return quantity.multiply(entryPrice.subtract(currentPrice));
        }
    }

    /**
     * Calculate unrealized profit/loss percentage.
     * Returns percentage change from entry price.
     */
    public BigDecimal getUnrealizedPnLPercentage(BigDecimal currentPrice) {
        BigDecimal pnl = getUnrealizedPnL(currentPrice);
        BigDecimal initialValue = quantity.multiply(entryPrice);

        if (initialValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return pnl.divide(initialValue, 6, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100));
    }

    public Instant getOpenInstant() {
        return Instant.ofEpochMilli(openTimestamp);
    }

    @Override
    public String toString() {
        return String.format("Position[%s %s qty=%.4f entry=%.2f @%d]",
            id.substring(0, 8), side, quantity, entryPrice, openTimestamp);
    }
}
