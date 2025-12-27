package com.tradingbot.backtest.engine;

import com.tradingbot.core.models.Portfolio;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Checks conditions for interrupting a simulation.
 * Simulation should be interrupted if:
 * 1. Portfolio equity drops below 25% of maximum equity
 * 2. Portfolio equity drops below 25% of initial balance
 */
@Slf4j
public class SimulationInterruptor {

    private static final BigDecimal TWENTY_FIVE_PERCENT = new BigDecimal("0.25");

    private final BigDecimal initialBalance;
    private final BigDecimal minEquityFromInitial;

    public SimulationInterruptor(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
        this.minEquityFromInitial = initialBalance.multiply(TWENTY_FIVE_PERCENT);
    }

    /**
     * Check if simulation should be interrupted.
     *
     * @param portfolio Current portfolio state
     * @param currentPrice Current market price
     * @return Interruption reason if should interrupt, null otherwise
     */
    public String shouldInterrupt(Portfolio portfolio, BigDecimal currentPrice) {
        BigDecimal currentEquity = portfolio.getEquity(currentPrice);
        BigDecimal maxEquity = portfolio.getMaxEquity();

        // Condition 1: Below 25% of max equity
        BigDecimal minEquityFromMax = maxEquity.multiply(TWENTY_FIVE_PERCENT);
        if (currentEquity.compareTo(minEquityFromMax) < 0) {
            BigDecimal dropPercentage = calculateDropPercentage(maxEquity, currentEquity);
            String reason = String.format(
                "Equity dropped below 25%% of max (current: %.2f, max: %.2f, drop: %.2f%%)",
                currentEquity, maxEquity, dropPercentage
            );
            log.warn("Simulation interrupted: {}", reason);
            return reason;
        }

        // Condition 2: Below 25% of initial balance
        if (currentEquity.compareTo(minEquityFromInitial) < 0) {
            BigDecimal dropPercentage = calculateDropPercentage(initialBalance, currentEquity);
            String reason = String.format(
                "Equity dropped below 25%% of initial (current: %.2f, initial: %.2f, drop: %.2f%%)",
                currentEquity, initialBalance, dropPercentage
            );
            log.warn("Simulation interrupted: {}", reason);
            return reason;
        }

        return null;  // No interruption
    }

    private BigDecimal calculateDropPercentage(BigDecimal from, BigDecimal to) {
        if (from.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return from.subtract(to)
                  .divide(from, 6, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100));
    }
}
