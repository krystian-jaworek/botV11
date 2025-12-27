package com.tradingbot.core.models;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Immutable representation of a closed trading position.
 * Contains realized profit/loss information.
 */
@Value
@Builder
public class ClosedPosition {
    String id;
    OrderSide side;
    BigDecimal entryPrice;
    BigDecimal exitPrice;
    BigDecimal quantity;
    long openTimestamp;
    long closeTimestamp;
    String metadata;

    /**
     * Create from an open position
     */
    public static ClosedPosition fromPosition(Position position, BigDecimal exitPrice, long closeTimestamp) {
        return ClosedPosition.builder()
            .id(position.getId())
            .side(position.getSide())
            .entryPrice(position.getEntryPrice())
            .exitPrice(exitPrice)
            .quantity(position.getQuantity())
            .openTimestamp(position.getOpenTimestamp())
            .closeTimestamp(closeTimestamp)
            .metadata(position.getMetadata())
            .build();
    }

    /**
     * Calculate realized profit/loss.
     * For LONG: PnL = quantity * (exitPrice - entryPrice)
     * For SHORT: PnL = quantity * (entryPrice - exitPrice)
     */
    public BigDecimal getRealizedPnL() {
        if (side == OrderSide.LONG) {
            return quantity.multiply(exitPrice.subtract(entryPrice));
        } else {
            return quantity.multiply(entryPrice.subtract(exitPrice));
        }
    }

    /**
     * Calculate realized profit/loss percentage
     */
    public BigDecimal getRealizedPnLPercentage() {
        BigDecimal pnl = getRealizedPnL();
        BigDecimal initialValue = quantity.multiply(entryPrice);

        if (initialValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return pnl.divide(initialValue, 6, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Get duration of the position in milliseconds
     */
    public long getDurationMillis() {
        return closeTimestamp - openTimestamp;
    }

    public Instant getOpenInstant() {
        return Instant.ofEpochMilli(openTimestamp);
    }

    public Instant getCloseInstant() {
        return Instant.ofEpochMilli(closeTimestamp);
    }

    @Override
    public String toString() {
        return String.format("ClosedPosition[%s %s qty=%.4f entry=%.2f exit=%.2f PnL=%.2f (%.2f%%)]",
            id.substring(0, 8), side, quantity, entryPrice, exitPrice,
            getRealizedPnL(), getRealizedPnLPercentage());
    }
}
