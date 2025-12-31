package com.tradingbot.algorithms.smaopportunistic;

import com.tradingbot.core.algorithms.AlgorithmState;
import com.tradingbot.core.algorithms.TradingAlgorithm;
import com.tradingbot.core.algorithms.TradingDecision;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.Portfolio;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * SMA Opportunistic Algorithm
 *
 * Simple Moving Average based opportunistic trading strategy.
 * Calculates SMA based on closing prices and uses it for trading decisions.
 */
@Slf4j
public class SMAOpportunisticAlgorithm implements TradingAlgorithm<SMAOpportunisticConfig> {

    private final SMAOpportunisticConfig config;
    private final List<Candle> candleHistory;

    public SMAOpportunisticAlgorithm(SMAOpportunisticConfig config) {
        this.config = config;
        this.config.validate();
        this.candleHistory = new ArrayList<>();
    }

    @Override
    public String getName() {
        return "SMA Opportunistic";
    }

    @Override
    public SMAOpportunisticConfig getConfig() {
        return config;
    }

    @Override
    public void initialize(BigDecimal initialPrice) {
        log.info("Initializing {} algorithm", getName());
        log.info("Config: {}", config.getConfigId());
        log.info("SMA period: {}", config.getSmaPeriod());
    }

    @Override
    public TradingDecision onCandle(Candle candle, Portfolio portfolio) {
        // Add candle to history
        candleHistory.add(candle);

        // Need enough candles for SMA calculation
        if (candleHistory.size() < config.getSmaPeriod()) {
            return TradingDecision.Hold.INSTANCE;
        }

        // Calculate current SMA
        BigDecimal currentSMA = calculateSMA(config.getSmaPeriod());
        if (currentSMA == null) {
            return TradingDecision.Hold.INSTANCE;
        }

        BigDecimal currentPrice = candle.close();

        log.debug("Candle {}: price={}, SMA({})={}",
            candleHistory.size(), currentPrice, config.getSmaPeriod(), currentSMA);

        // TODO: Implement trading logic
        // For now, just hold
        return TradingDecision.Hold.INSTANCE;
    }

    @Override
    public AlgorithmState getState() {
        // Return empty state for now
        return AlgorithmState.builder()
            .algorithmName(getName())
            .build();
    }

    @Override
    public void restoreState(AlgorithmState state) {
        // TODO: Implement state restoration if needed
    }

    /**
     * Calculate Simple Moving Average based on closing prices
     *
     * @param period Number of candles to include in SMA
     * @return SMA value or null if not enough data
     */
    private BigDecimal calculateSMA(int period) {
        if (candleHistory.size() < period) {
            return null;
        }

        // Get last N candles
        int startIndex = candleHistory.size() - period;
        List<Candle> relevantCandles = candleHistory.subList(startIndex, candleHistory.size());

        // Calculate sum of closing prices
        BigDecimal sum = BigDecimal.ZERO;
        for (Candle c : relevantCandles) {
            sum = sum.add(c.close());
        }

        // Calculate average
        BigDecimal sma = sum.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
        return sma;
    }
}
