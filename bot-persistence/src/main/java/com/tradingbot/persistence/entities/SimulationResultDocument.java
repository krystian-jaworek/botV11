package com.tradingbot.persistence.entities;

import com.tradingbot.core.metrics.SimulationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * MongoDB document for storing simulation results.
 * Collection name is dynamic: <AlgorithmName>-<TradingPair>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document  // Collection name set dynamically
@CompoundIndexes({
    @CompoundIndex(name = "profit_idx", def = "{'profitPercentage': -1, 'profitAbsolute': -1}"),
    @CompoundIndex(name = "drawdown_idx", def = "{'maxPortfolioDrawdownPercentage': 1, 'maxPositionDrawdownPercentage': 1}"),
    @CompoundIndex(name = "interrupted_idx", def = "{'interrupted': 1, 'profitPercentage': -1}")
})
public class SimulationResultDocument {

    @Id
    private String id;

    // Metadata
    @Indexed
    private String algorithmName;

    @Indexed
    private String tradingPair;

    private Instant createdAt;

    // Timestamps
    private long startTimestamp;
    private long endTimestamp;

    // Status
    @Indexed
    private boolean interrupted;
    private String interruptionReason;

    // Initial state
    private BigDecimal initialBalance;

    // Final state
    private BigDecimal finalCashBalance;
    private BigDecimal finalEquity;
    private int totalTradesExecuted;
    private int openPositionsAtEnd;

    // Profit metrics
    @Indexed
    private BigDecimal profitAbsolute;

    @Indexed
    private BigDecimal profitPercentage;

    // Equity metrics
    private BigDecimal maxEquityWithPositions;
    private BigDecimal minEquityWithPositions;
    private BigDecimal maxCashBalance;
    private BigDecimal minCashBalance;

    // Drawdown metrics
    @Indexed
    private BigDecimal maxPositionDrawdownPercentage;

    @Indexed
    private BigDecimal maxPortfolioDrawdownPercentage;

    // Configuration (stored as JSON string or embedded document)
    private String configJson;

    /**
     * Convert from core SimulationResult
     */
    public static SimulationResultDocument fromSimulationResult(SimulationResult result, String configJson) {
        return SimulationResultDocument.builder()
            .algorithmName(result.getAlgorithmName())
            .tradingPair(result.getTradingPair())
            .createdAt(Instant.now())
            .startTimestamp(result.getStartTimestamp())
            .endTimestamp(result.getEndTimestamp())
            .interrupted(result.isInterrupted())
            .interruptionReason(result.getInterruptionReason())
            .initialBalance(result.getInitialBalance())
            .finalCashBalance(result.getFinalCashBalance())
            .finalEquity(result.getFinalEquity())
            .totalTradesExecuted(result.getTotalTradesExecuted())
            .openPositionsAtEnd(result.getOpenPositionsAtEnd())
            .profitAbsolute(result.getProfitAbsolute())
            .profitPercentage(result.getProfitPercentage())
            .maxEquityWithPositions(result.getMaxEquityWithPositions())
            .minEquityWithPositions(result.getMinEquityWithPositions())
            .maxCashBalance(result.getMaxCashBalance())
            .minCashBalance(result.getMinCashBalance())
            .maxPositionDrawdownPercentage(result.getMaxPositionDrawdownPercentage())
            .maxPortfolioDrawdownPercentage(result.getMaxPortfolioDrawdownPercentage())
            .configJson(configJson)
            .build();
    }

    /**
     * Convert to core SimulationResult
     */
    public SimulationResult toSimulationResult() {
        return SimulationResult.builder()
            .algorithmName(algorithmName)
            .tradingPair(tradingPair)
            .startTimestamp(startTimestamp)
            .endTimestamp(endTimestamp)
            .interrupted(interrupted)
            .interruptionReason(interruptionReason)
            .initialBalance(initialBalance)
            .finalCashBalance(finalCashBalance)
            .finalEquity(finalEquity)
            .totalTradesExecuted(totalTradesExecuted)
            .openPositionsAtEnd(openPositionsAtEnd)
            .profitAbsolute(profitAbsolute)
            .profitPercentage(profitPercentage)
            .maxEquityWithPositions(maxEquityWithPositions)
            .minEquityWithPositions(minEquityWithPositions)
            .maxCashBalance(maxCashBalance)
            .minCashBalance(minCashBalance)
            .maxPositionDrawdownPercentage(maxPositionDrawdownPercentage)
            .maxPortfolioDrawdownPercentage(maxPortfolioDrawdownPercentage)
            .build();
    }
}
