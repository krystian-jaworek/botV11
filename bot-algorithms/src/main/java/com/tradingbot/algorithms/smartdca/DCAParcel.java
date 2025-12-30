package com.tradingbot.algorithms.smartdca;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents a single DCA purchase (sub-position).
 * Multiple parcels together form the complete DCA position.
 */
@Value
@Builder
public class DCAParcel {
    String id;
    long timestamp;
    BigDecimal entryPrice;
    BigDecimal quantity;
    BigDecimal investedAmount;
    String tierName;  // e.g., "Tier 1 - Small Dip"

    /**
     * Create a new parcel with auto-generated ID
     */
    public static DCAParcel create(long timestamp, BigDecimal entryPrice,
                                   BigDecimal quantity, BigDecimal investedAmount,
                                   String tierName) {
        return DCAParcel.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(timestamp)
            .entryPrice(entryPrice)
            .quantity(quantity)
            .investedAmount(investedAmount)
            .tierName(tierName)
            .build();
    }

    /**
     * Calculate profit/loss for this parcel at current price
     */
    public BigDecimal calculatePnL(BigDecimal currentPrice) {
        return quantity.multiply(currentPrice.subtract(entryPrice));
    }

    /**
     * Calculate profit/loss percentage for this parcel
     */
    public BigDecimal calculatePnLPercentage(BigDecimal currentPrice) {
        if (investedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return calculatePnL(currentPrice)
            .divide(investedAmount, 6, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }
}
