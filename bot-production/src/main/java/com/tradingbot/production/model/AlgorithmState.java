package com.tradingbot.production.model;

import com.tradingbot.core.models.Position;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the runtime state of an algorithm instance.
 * This is embedded in AlgorithmInstance document and captured after each candle processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmState {

    // Portfolio state
    private BigDecimal cashBalance;
    private BigDecimal initialBalance;

    // Position state (null if no position)
    private Position currentPosition;

    // Algorithm-specific state (for SMAOpportunistic)
    private Instant lastBuyTimestamp;
    private BigDecimal lastBuyPrice;

    // SMA calculation state (for sliding window optimization)
    @Builder.Default
    private List<BigDecimal> candleClosePrices = new ArrayList<>();
    private BigDecimal smaSum;
    private Integer smaCount;

    // Performance metrics (tracked during runtime)
    private BigDecimal maxEquity;
    private BigDecimal minCashBalance;
    private BigDecimal maxPositionDrawdownPercentage;
    private Integer totalTrades;

    /**
     * Create initial state from account balance
     */
    public static AlgorithmState createInitialState(BigDecimal accountBalance) {
        return AlgorithmState.builder()
            .cashBalance(accountBalance)
            .initialBalance(accountBalance)
            .currentPosition(null)
            .lastBuyTimestamp(null)
            .lastBuyPrice(null)
            .candleClosePrices(new ArrayList<>())
            .smaSum(BigDecimal.ZERO)
            .smaCount(0)
            .maxEquity(accountBalance)
            .minCashBalance(accountBalance)
            .maxPositionDrawdownPercentage(BigDecimal.ZERO)
            .totalTrades(0)
            .build();
    }
}
