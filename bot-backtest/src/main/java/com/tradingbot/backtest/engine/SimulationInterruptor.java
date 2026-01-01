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
 * 3. (Early stopping) After 50% of candles processed, if loss > 50%
 * 4. (Early stopping) After 25% of candles processed, if no trades executed
 */
@Slf4j
public class SimulationInterruptor {

    private static final BigDecimal TWENTY_FIVE_PERCENT = new BigDecimal("0.25");
    private static final BigDecimal FIFTY_PERCENT = new BigDecimal("0.50");

    private final BigDecimal initialBalance;
    private final BigDecimal minEquityFromInitial;
    private final int totalCandles;

    private boolean hasHadAnyTrades = false;

    public SimulationInterruptor(BigDecimal initialBalance, int totalCandles) {
        this.initialBalance = initialBalance;
        this.minEquityFromInitial = initialBalance.multiply(TWENTY_FIVE_PERCENT);
        this.totalCandles = totalCandles;
    }

    /**
     * Notify that a trade was executed
     */
    public void notifyTradeExecuted() {
        this.hasHadAnyTrades = true;
    }

    /**
     * Check if simulation should be interrupted.
     *
     * @param portfolio Current portfolio state
     * @param currentPrice Current market price
     * @param processedCandles Number of candles processed so far
     * @return Interruption reason if should interrupt, null otherwise
     */
    public String shouldInterrupt(Portfolio portfolio, BigDecimal currentPrice, int processedCandles) {
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

        // Early stopping condition 3: After 50% of candles, if loss > 50%
        if (processedCandles >= totalCandles / 2) {
            BigDecimal lossThreshold = initialBalance.multiply(FIFTY_PERCENT);
            if (currentEquity.compareTo(lossThreshold) < 0) {
                BigDecimal dropPercentage = calculateDropPercentage(initialBalance, currentEquity);
                String reason = String.format(
                    "Early stop: Loss > 50%% after 50%% of simulation (current: %.2f, initial: %.2f, drop: %.2f%%)",
                    currentEquity, initialBalance, dropPercentage
                );
                log.info("Simulation interrupted (early stop): {}", reason);
                return reason;
            }
        }

        // Early stopping condition 4: After 25% of candles, if no trades executed
        if (processedCandles >= totalCandles / 4) {
            if (!hasHadAnyTrades) {
                String reason = String.format(
                    "Early stop: No trades after 25%% of simulation (%d/%d candles)",
                    processedCandles, totalCandles
                );
                log.info("Simulation interrupted (early stop): {}", reason);
                return reason;
            }
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
