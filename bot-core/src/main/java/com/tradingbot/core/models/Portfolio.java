package com.tradingbot.core.models;

import lombok.Data;

import java.math.BigDecimal;
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
     * Close a position and add cash from proceeds
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
