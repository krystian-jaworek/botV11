package com.tradingbot.core.portfolio;

import com.tradingbot.core.models.Portfolio;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Immutable snapshot of portfolio state at a specific point in time.
 * Used for reporting and logging.
 */
@Value
@Builder
public class PortfolioSnapshot {
    long timestamp;
    BigDecimal cashBalance;
    BigDecimal equity;
    int openPositionsCount;
    int closedPositionsCount;
    BigDecimal totalRealizedPnL;
    BigDecimal totalUnrealizedPnL;
    BigDecimal maxEquity;
    BigDecimal minEquity;

    /**
     * Create snapshot from portfolio
     */
    public static PortfolioSnapshot from(Portfolio portfolio, BigDecimal currentPrice, long timestamp) {
        return PortfolioSnapshot.builder()
            .timestamp(timestamp)
            .cashBalance(portfolio.getCashBalance())
            .equity(portfolio.getEquity(currentPrice))
            .openPositionsCount(portfolio.getOpenPositionCount())
            .closedPositionsCount(portfolio.getClosedPositionCount())
            .totalRealizedPnL(portfolio.getTotalRealizedPnL())
            .totalUnrealizedPnL(portfolio.getTotalUnrealizedPnL(currentPrice))
            .maxEquity(portfolio.getMaxEquity())
            .minEquity(portfolio.getMinEquity())
            .build();
    }

    @Override
    public String toString() {
        return String.format(
            "PortfolioSnapshot[cash=%.2f equity=%.2f open=%d closed=%d realizedPnL=%.2f unrealizedPnL=%.2f]",
            cashBalance, equity, openPositionsCount, closedPositionsCount,
            totalRealizedPnL, totalUnrealizedPnL
        );
    }
}
