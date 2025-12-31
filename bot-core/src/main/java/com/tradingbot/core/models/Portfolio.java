package com.tradingbot.core.models;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable representation of a trading portfolio.
 * Tracks cash balance, open positions, and closed positions.
 *
 * NOT thread-safe - each simulation should have its own instance.
 */
@Data
public class Portfolio {
    private final BigDecimal initialBalance;
    private BigDecimal cashBalance;
    private final Map<String, Position> openPositions;
    private final List<ClosedPosition> closedPositions;

    // Track max/min equity for metrics
    private BigDecimal maxEquity;
    private BigDecimal minEquity;
    private BigDecimal maxCashBalance;
    private BigDecimal minCashBalance;

    public Portfolio(BigDecimal initialBalance) {
        if (initialBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial balance must be positive");
        }

        this.initialBalance = initialBalance;
        this.cashBalance = initialBalance;
        this.openPositions = new HashMap<>();
        this.closedPositions = new ArrayList<>();

        this.maxEquity = initialBalance;
        this.minEquity = initialBalance;
        this.maxCashBalance = initialBalance;
        this.minCashBalance = initialBalance;
    }

    /**
     * Add an open position and deduct cash
     */
    public void addPosition(Position position) {
        BigDecimal cost = position.getQuantity().multiply(position.getEntryPrice());

        if (cashBalance.compareTo(cost) < 0) {
            throw new IllegalStateException(
                String.format("Insufficient cash balance. Required: %.2f, Available: %.2f",
                    cost, cashBalance)
            );
        }

        openPositions.put(position.getId(), position);
        cashBalance = cashBalance.subtract(cost);
        updateMinCashBalance();
    }

    /**
     * Increase position size (futures-style DCA).
     * Calculates new weighted average entry price.
     *
     * @param positionId Position to increase
     * @param additionalQuantity Additional quantity to add
     * @param purchasePrice Price of the additional purchase
     * @return Updated position with new quantity and weighted avg entry
     */
    public Position increasePosition(String positionId, BigDecimal additionalQuantity, BigDecimal purchasePrice) {
        Position oldPosition = openPositions.get(positionId);
        if (oldPosition == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }

        BigDecimal additionalCost = additionalQuantity.multiply(purchasePrice);

        if (cashBalance.compareTo(additionalCost) < 0) {
            throw new IllegalStateException(
                String.format("Insufficient cash balance. Required: %.2f, Available: %.2f",
                    additionalCost, cashBalance)
            );
        }

        // Calculate weighted average entry price
        BigDecimal oldCost = oldPosition.getQuantity().multiply(oldPosition.getEntryPrice());
        BigDecimal totalCost = oldCost.add(additionalCost);
        BigDecimal newQuantity = oldPosition.getQuantity().add(additionalQuantity);
        BigDecimal newAvgEntry = totalCost.divide(newQuantity, 8, RoundingMode.HALF_UP);

        // Create updated position (Position is immutable, so create new instance)
        Position updatedPosition = Position.builder()
            .id(oldPosition.getId())  // Keep same ID!
            .side(oldPosition.getSide())
            .entryPrice(newAvgEntry)  // Updated weighted average
            .quantity(newQuantity)    // Increased quantity
            .openTimestamp(oldPosition.getOpenTimestamp())  // Original open time
            .metadata(oldPosition.getMetadata())
            .build();

        // Replace old position with updated one
        openPositions.put(positionId, updatedPosition);

        // Deduct cost
        cashBalance = cashBalance.subtract(additionalCost);
        updateMinCashBalance();

        return updatedPosition;
    }

    /**
     * Close a position (fully) and add cash from proceeds
     */
    public ClosedPosition closePosition(String positionId, BigDecimal exitPrice, long closeTimestamp) {
        Position position = openPositions.remove(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }

        ClosedPosition closedPosition = ClosedPosition.fromPosition(position, exitPrice, closeTimestamp);

        // Add proceeds to cash
        BigDecimal proceeds = calculateProceeds(position, exitPrice);
        cashBalance = cashBalance.add(proceeds);

        closedPositions.add(closedPosition);

        updateMaxCashBalance();
        return closedPosition;
    }

    /**
     * Close part of a position and add partial proceeds to cash.
     * The position remains open with reduced quantity.
     *
     * @param positionId Position to partially close
     * @param quantityToClose How much to close
     * @param exitPrice Exit price
     * @param closeTimestamp Timestamp of close
     * @return ClosedPosition representing the partial close
     */
    public ClosedPosition closePartialPosition(
        String positionId,
        BigDecimal quantityToClose,
        BigDecimal exitPrice,
        long closeTimestamp
    ) {
        Position position = openPositions.get(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }

        if (quantityToClose.compareTo(position.getQuantity()) > 0) {
            throw new IllegalArgumentException(
                String.format("Cannot close more than available: %s > %s",
                    quantityToClose, position.getQuantity())
            );
        }

        // Calculate proceeds for the closed portion
        BigDecimal portionProceeds;
        if (position.getSide() == OrderSide.LONG) {
            portionProceeds = quantityToClose.multiply(exitPrice);
        } else {
            // SHORT: return portion of initial margin + portion of PnL
            BigDecimal initialCost = quantityToClose.multiply(position.getEntryPrice());
            BigDecimal portionPnL = quantityToClose.multiply(exitPrice.subtract(position.getEntryPrice()));
            if (position.getSide() == OrderSide.SHORT) {
                portionPnL = portionPnL.negate();
            }
            portionProceeds = initialCost.add(portionPnL);
        }

        cashBalance = cashBalance.add(portionProceeds);

        // Update position with reduced quantity
        BigDecimal remainingQuantity = position.getQuantity().subtract(quantityToClose);

        if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            // Closing the last portion - remove position entirely
            openPositions.remove(positionId);
        } else {
            // Update position quantity
            Position updatedPosition = new Position(
                position.getId(),
                position.getSide(),
                remainingQuantity,
                position.getEntryPrice(),
                position.getOpenTimestamp(),
                position.getMetadata()
            );
            openPositions.put(positionId, updatedPosition);
        }

        // Create ClosedPosition record for this partial close
        ClosedPosition closedPosition = ClosedPosition.fromPartialClose(
            position,
            quantityToClose,
            exitPrice,
            closeTimestamp,
            remainingQuantity
        );

        closedPositions.add(closedPosition);
        updateMaxCashBalance();

        return closedPosition;
    }

    /**
     * Calculate proceeds from closing a position.
     * For LONG: proceeds = quantity * exitPrice
     * For SHORT: proceeds = quantity * entryPrice + PnL
     */
    private BigDecimal calculateProceeds(Position position, BigDecimal exitPrice) {
        if (position.getSide() == OrderSide.LONG) {
            return position.getQuantity().multiply(exitPrice);
        } else {
            // SHORT: return initial margin + PnL
            BigDecimal initialCost = position.getQuantity().multiply(position.getEntryPrice());
            BigDecimal pnl = position.getUnrealizedPnL(exitPrice);
            return initialCost.add(pnl);
        }
    }

    /**
     * Calculate total equity: cash + value of open positions
     */
    public BigDecimal getEquity(BigDecimal currentPrice) {
        BigDecimal openPositionsValue = openPositions.values().stream()
            .map(p -> p.getCurrentValue(currentPrice))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return cashBalance.add(openPositionsValue);
    }

    /**
     * Update max equity if current equity is higher
     */
    public void updateMaxEquity(BigDecimal currentPrice) {
        BigDecimal currentEquity = getEquity(currentPrice);
        if (currentEquity.compareTo(maxEquity) > 0) {
            maxEquity = currentEquity;
        }
    }

    /**
     * Update min equity if current equity is lower
     */
    public void updateMinEquity(BigDecimal currentPrice) {
        BigDecimal currentEquity = getEquity(currentPrice);
        if (currentEquity.compareTo(minEquity) < 0) {
            minEquity = currentEquity;
        }
    }

    private void updateMaxCashBalance() {
        if (cashBalance.compareTo(maxCashBalance) > 0) {
            maxCashBalance = cashBalance;
        }
    }

    private void updateMinCashBalance() {
        if (cashBalance.compareTo(minCashBalance) < 0) {
            minCashBalance = cashBalance;
        }
    }

    /**
     * Get total unrealized PnL from all open positions
     */
    public BigDecimal getTotalUnrealizedPnL(BigDecimal currentPrice) {
        return openPositions.values().stream()
            .map(p -> p.getUnrealizedPnL(currentPrice))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total realized PnL from all closed positions
     */
    public BigDecimal getTotalRealizedPnL() {
        return closedPositions.stream()
            .map(ClosedPosition::getRealizedPnL)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total PnL (realized + unrealized)
     */
    public BigDecimal getTotalPnL(BigDecimal currentPrice) {
        return getTotalRealizedPnL().add(getTotalUnrealizedPnL(currentPrice));
    }

    /**
     * Check if portfolio has enough cash for a trade
     */
    public boolean hasEnoughCash(BigDecimal requiredAmount) {
        return cashBalance.compareTo(requiredAmount) >= 0;
    }

    /**
     * Get number of open positions
     */
    public int getOpenPositionCount() {
        return openPositions.size();
    }

    /**
     * Get number of closed positions
     */
    public int getClosedPositionCount() {
        return closedPositions.size();
    }

    @Override
    public String toString() {
        return String.format("Portfolio[cash=%.2f open=%d closed=%d equity=N/A]",
            cashBalance, openPositions.size(), closedPositions.size());
    }
}
