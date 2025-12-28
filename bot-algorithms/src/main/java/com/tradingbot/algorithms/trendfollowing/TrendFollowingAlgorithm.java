package com.tradingbot.algorithms.trendfollowing;

import com.tradingbot.core.algorithms.AlgorithmState;
import com.tradingbot.core.algorithms.TradingAlgorithm;
import com.tradingbot.core.algorithms.TradingDecision;
import com.tradingbot.core.indicators.*;
import com.tradingbot.core.indicators.MACD.MACDResult;
import com.tradingbot.core.indicators.SwingDetector.SwingPoint;
import com.tradingbot.core.models.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Trend Following algorithm with multiple timeframes.
 *
 * Entry conditions (ALL must be true):
 * 1. Golden cross on primary timeframe
 * 2. Golden cross OR fast > slow on secondary timeframe
 * 3. N higher lows detected in lookback period
 * 4. MACD histogram growing on both timeframes
 *
 * Exit conditions (priority order):
 * 1. Stop Loss
 * 2. Trailing Stop
 * 3. Death Cross
 * 4. Take Profit levels (partial closes)
 */
@Slf4j
public class TrendFollowingAlgorithm implements TradingAlgorithm<TrendFollowingConfig> {

    private final TrendFollowingConfig config;
    private final TrendFollowingState state;

    // Aggregated candles for secondary timeframe
    private List<Candle> primaryCandles;
    private List<Candle> secondaryCandles;

    private BigDecimal initialBalance;

    public TrendFollowingAlgorithm(TrendFollowingConfig config) {
        this.config = config;
        this.state = new TrendFollowingState();
    }

    @Override
    public String getName() {
        return "TrendFollowing";
    }

    @Override
    public TrendFollowingConfig getConfig() {
        return config;
    }

    @Override
    public AlgorithmState getState() {
        AlgorithmState algorithmState = AlgorithmState.builder()
            .algorithmName(getName())
            .lastUpdateTimestamp(System.currentTimeMillis())
            .build();

        // Save position tracking (indicators are pre-calculated, no need to persist)
        algorithmState.putState("currentPositionId", state.getCurrentPositionId());
        if (state.getEntryPrice() != null) {
            algorithmState.putState("entryPrice", state.getEntryPrice().toString());
        }
        if (state.getInitialQuantity() != null) {
            algorithmState.putState("initialQuantity", state.getInitialQuantity().toString());
        }
        if (state.getRemainingQuantity() != null) {
            algorithmState.putState("remainingQuantity", state.getRemainingQuantity().toString());
        }
        algorithmState.putState("tpLevelsHit", new ArrayList<>(state.getTpLevelsHit()));

        // Dynamic TP prices
        List<String> tpPriceStrings = new ArrayList<>();
        for (BigDecimal tp : state.getDynamicTpPrices()) {
            tpPriceStrings.add(tp.toString());
        }
        algorithmState.putState("dynamicTpPrices", tpPriceStrings);

        // Trailing stop
        if (state.getTrailingStopPrice() != null) {
            algorithmState.putState("trailingStopPrice", state.getTrailingStopPrice().toString());
        }
        algorithmState.putState("trailingStopActive", state.isTrailingStopActive());
        if (state.getHighestPriceSinceEntry() != null) {
            algorithmState.putState("highestPriceSinceEntry", state.getHighestPriceSinceEntry().toString());
        }

        return algorithmState;
    }

    @Override
    public void restoreState(AlgorithmState algorithmState) {
        log.info("Restoring TrendFollowing state");

        // Restore position tracking
        state.setCurrentPositionId((String) algorithmState.getStateData().get("currentPositionId"));

        String entryPriceStr = (String) algorithmState.getStateData().get("entryPrice");
        if (entryPriceStr != null) {
            state.setEntryPrice(new BigDecimal(entryPriceStr));
        }

        String initialQtyStr = (String) algorithmState.getStateData().get("initialQuantity");
        if (initialQtyStr != null) {
            state.setInitialQuantity(new BigDecimal(initialQtyStr));
        }

        String remainingQtyStr = (String) algorithmState.getStateData().get("remainingQuantity");
        if (remainingQtyStr != null) {
            state.setRemainingQuantity(new BigDecimal(remainingQtyStr));
        }

        @SuppressWarnings("unchecked")
        List<Integer> tpHitList = (List<Integer>) algorithmState.getStateData().get("tpLevelsHit");
        if (tpHitList != null) {
            state.setTpLevelsHit(new java.util.HashSet<>(tpHitList));
        }

        // Dynamic TP prices
        @SuppressWarnings("unchecked")
        List<String> tpPriceStrings = (List<String>) algorithmState.getStateData().get("dynamicTpPrices");
        if (tpPriceStrings != null) {
            List<BigDecimal> tpPrices = new ArrayList<>();
            for (String tpStr : tpPriceStrings) {
                tpPrices.add(new BigDecimal(tpStr));
            }
            state.setDynamicTpPrices(tpPrices);
        }

        // Trailing stop
        String trailingStr = (String) algorithmState.getStateData().get("trailingStopPrice");
        if (trailingStr != null) {
            state.setTrailingStopPrice(new BigDecimal(trailingStr));
        }

        Boolean trailingActive = (Boolean) algorithmState.getStateData().get("trailingStopActive");
        if (trailingActive != null) {
            state.setTrailingStopActive(trailingActive);
        }

        String highestStr = (String) algorithmState.getStateData().get("highestPriceSinceEntry");
        if (highestStr != null) {
            state.setHighestPriceSinceEntry(new BigDecimal(highestStr));
        }

        log.info("TrendFollowing state restored: {}", state);
    }

    @Override
    public void initialize(BigDecimal initialPrice) {
        log.debug("Initializing TrendFollowing with price: {}", initialPrice);
        // Initial setup - indicators will be calculated as candles arrive
        state.setCurrentPrimaryCandleIndex(0);
        state.setCurrentSecondaryCandleIndex(0);
    }

    /**
     * Pre-calculate all indicators from historical candles.
     * This is called before backtesting starts with full historical data.
     */
    public void preCalculateIndicators(List<Candle> allCandles) {
        log.debug("Pre-calculating indicators for {} candles", allCandles.size());

        this.primaryCandles = allCandles;

        // Aggregate to secondary timeframe
        this.secondaryCandles = TimeframeAggregator.aggregate(
            allCandles,
            config.getPrimaryTimeframe(),
            config.getSecondaryTimeframe()
        );

        log.debug("Aggregated {} primary candles → {} secondary candles",
            primaryCandles.size(), secondaryCandles.size());

        // Calculate primary timeframe indicators
        state.setEmaFastPrimary(ExponentialMovingAverage.calculate(primaryCandles, config.getEmaFastPrimary()));
        state.setEmaSlowPrimary(ExponentialMovingAverage.calculate(primaryCandles, config.getEmaSlowPrimary()));
        state.setMacdPrimary(MACD.calculate(
            primaryCandles,
            config.getMacdFast(),
            config.getMacdSlow(),
            config.getMacdSignal()
        ));
        state.setSwingLowsPrimary(SwingDetector.findSwingLows(
            primaryCandles,
            config.getSwingDetectionPeriods()
        ));

        // Calculate secondary timeframe indicators
        state.setEmaFastSecondary(ExponentialMovingAverage.calculate(secondaryCandles, config.getEmaFastSecondary()));
        state.setEmaSlowSecondary(ExponentialMovingAverage.calculate(secondaryCandles, config.getEmaSlowSecondary()));
        state.setMacdSecondary(MACD.calculate(
            secondaryCandles,
            config.getMacdFast(),
            config.getMacdSlow(),
            config.getMacdSignal()
        ));

        log.debug("Indicators calculated - Primary EMA Fast: {}, Slow: {}, Swings: {}",
            state.getEmaFastPrimary().size(),
            state.getEmaSlowPrimary().size(),
            state.getSwingLowsPrimary().size()
        );
    }

    @Override
    public TradingDecision onCandle(Candle candle, Portfolio portfolio) {
        if (primaryCandles == null) {
            log.warn("Indicators not pre-calculated. Call preCalculateIndicators() first.");
            return new TradingDecision.Hold();
        }

        // Store initial balance for position sizing
        if (initialBalance == null) {
            initialBalance = portfolio.getInitialBalance();
        }

        int currentIndex = state.getCurrentPrimaryCandleIndex();
        state.setCurrentPrimaryCandleIndex(currentIndex + 1);

        // Update highest price if we have an open position
        if (state.hasOpenPosition()) {
            state.updateHighestPrice(candle.close());
        }

        // Route to appropriate logic
        if (state.hasOpenPosition()) {
            return checkExitConditions(candle, portfolio, currentIndex);
        } else {
            return checkEntryConditions(candle, portfolio, currentIndex);
        }
    }

    // ==================== ENTRY LOGIC ====================

    private TradingDecision checkEntryConditions(Candle candle, Portfolio portfolio, int currentIndex) {
        // Need enough data for all indicators
        int requiredData = Math.max(config.getEmaSlowPrimary(), config.getMacdSlow()) + config.getMacdSignal();
        if (currentIndex < requiredData) {
            log.debug("Not enough data for entry check at index {}", currentIndex);
            return new TradingDecision.Hold();
        }

        // CONDITION 1: Golden cross on primary timeframe
        if (!PatternValidator.isGoldenCross(
            state.getEmaFastPrimary(),
            state.getEmaSlowPrimary(),
            currentIndex
        )) {
            log.debug("No golden cross on primary timeframe at index {}", currentIndex);
            return new TradingDecision.Hold();
        }

        log.debug("✓ Golden cross detected on primary timeframe");

        // CONDITION 2: Golden cross OR fast > slow on secondary timeframe
        int secondaryIndex = getSecondaryIndexForPrimary(currentIndex);
        if (secondaryIndex < 0) {
            log.debug("Secondary timeframe not ready");
            return new TradingDecision.Hold();
        }

        boolean secondaryGoldenCross = PatternValidator.isGoldenCross(
            state.getEmaFastSecondary(),
            state.getEmaSlowSecondary(),
            secondaryIndex
        );

        boolean secondaryFastAboveSlow = PatternValidator.isFastAboveSlow(
            state.getEmaFastSecondary(),
            state.getEmaSlowSecondary(),
            secondaryIndex
        );

        if (!secondaryGoldenCross && !secondaryFastAboveSlow) {
            log.debug("Secondary timeframe condition not met");
            return new TradingDecision.Hold();
        }

        log.debug("✓ Secondary timeframe condition met (GC:{} | Fast>Slow:{})",
            secondaryGoldenCross, secondaryFastAboveSlow);

        // CONDITION 3: Higher lows pattern
        List<SwingPoint> recentSwings = getRecentSwingLows(currentIndex);
        if (!PatternValidator.hasHigherLows(recentSwings, config.getHigherLowsPeriods())) {
            log.debug("Higher lows pattern not found (swings: {})", recentSwings.size());
            return new TradingDecision.Hold();
        }

        log.debug("✓ Higher lows pattern detected ({} swings)", recentSwings.size());

        // CONDITION 4: MACD histogram growing on both timeframes
        if (!PatternValidator.isHistogramGrowing(
            state.getMacdPrimary(),
            config.getMinHistogramGrowth(),
            currentIndex
        )) {
            log.debug("Primary MACD histogram not growing");
            return new TradingDecision.Hold();
        }

        if (!PatternValidator.isHistogramGrowing(
            state.getMacdSecondary(),
            config.getMinHistogramGrowth(),
            secondaryIndex
        )) {
            log.debug("Secondary MACD histogram not growing");
            return new TradingDecision.Hold();
        }

        log.debug("✓ MACD histogram growing on both timeframes");

        // ALL CONDITIONS MET - OPEN POSITION
        return openPosition(candle, portfolio);
    }

    private TradingDecision openPosition(Candle candle, Portfolio portfolio) {
        BigDecimal quantity = calculatePositionSize(portfolio, candle.close());
        BigDecimal entryPrice = candle.close();

        // Calculate initial TP prices
        List<BigDecimal> tpPrices = new ArrayList<>();
        for (BigDecimal tpPct : config.getTargetProfitPct()) {
            BigDecimal tpPrice = entryPrice.multiply(
                BigDecimal.ONE.add(tpPct.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            );
            tpPrices.add(tpPrice);
        }

        String metadata = String.format(
            "TrendFollowing[Entry:GoldenCross+HigherLows+MACDGrowth, TPs:%s]",
            tpPrices
        );

        log.debug("✅ ENTRY SIGNAL - Opening LONG position at {} (qty: {}, TPs: {})",
            entryPrice, quantity, tpPrices);

        return new TradingDecision.OpenPosition(
            OrderSide.LONG,
            quantity,
            entryPrice,
            metadata
        );
    }

    // ==================== POSITION OPENED CALLBACK ====================

    @Override
    public void onPositionOpened(Position position) {
        // Calculate TP prices
        List<BigDecimal> tpPrices = new ArrayList<>();
        for (BigDecimal tpPct : config.getTargetProfitPct()) {
            BigDecimal tpPrice = position.getEntryPrice().multiply(
                BigDecimal.ONE.add(tpPct.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            );
            tpPrices.add(tpPrice);
        }

        state.initializePosition(
            position.getId(),
            position.getEntryPrice(),
            position.getQuantity(),
            tpPrices
        );

        log.debug("Position opened and tracked: {}", state);
    }

    // ==================== EXIT LOGIC ====================

    private TradingDecision checkExitConditions(Candle candle, Portfolio portfolio, int currentIndex) {
        BigDecimal currentPrice = candle.close();

        // PRIORITY 1: STOP LOSS (highest priority)
        TradingDecision slDecision = checkStopLoss(currentPrice);
        if (!(slDecision instanceof TradingDecision.Hold)) {
            return slDecision;
        }

        // PRIORITY 2: TRAILING STOP
        TradingDecision tsDecision = checkTrailingStop(currentPrice);
        if (!(tsDecision instanceof TradingDecision.Hold)) {
            return tsDecision;
        }

        // PRIORITY 3: DEATH CROSS
        if (config.isEnableDeathCrossExit()) {
            TradingDecision dcDecision = checkDeathCross(currentIndex);
            if (!(dcDecision instanceof TradingDecision.Hold)) {
                return dcDecision;
            }
        }

        // PRIORITY 4: TAKE PROFIT LEVELS (partial closes)
        TradingDecision tpDecision = checkTakeProfitLevels(currentPrice);
        if (!(tpDecision instanceof TradingDecision.Hold)) {
            return tpDecision;
        }

        // PRIORITY 5: UPDATE TRAILING STOP (if not active yet)
        updateTrailingStopLogic(currentPrice);

        return new TradingDecision.Hold();
    }

    private TradingDecision checkStopLoss(BigDecimal currentPrice) {
        BigDecimal stopLossPrice = state.getEntryPrice().multiply(
            BigDecimal.ONE.subtract(config.getStopLossPct().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
        );

        if (currentPrice.compareTo(stopLossPrice) <= 0) {
            log.debug("🛑 STOP LOSS HIT - Closing position at {} (SL: {})", currentPrice, stopLossPrice);
            return new TradingDecision.ClosePosition(state.getCurrentPositionId(), currentPrice);
        }

        return new TradingDecision.Hold();
    }

    private TradingDecision checkTrailingStop(BigDecimal currentPrice) {
        if (!state.isTrailingStopActive()) {
            return new TradingDecision.Hold();
        }

        if (currentPrice.compareTo(state.getTrailingStopPrice()) <= 0) {
            log.debug("📉 TRAILING STOP HIT - Closing position at {} (TS: {})",
                currentPrice, state.getTrailingStopPrice());
            return new TradingDecision.ClosePosition(state.getCurrentPositionId(), currentPrice);
        }

        return new TradingDecision.Hold();
    }

    private TradingDecision checkDeathCross(int currentIndex) {
        if (PatternValidator.isDeathCross(
            state.getEmaFastPrimary(),
            state.getEmaSlowPrimary(),
            currentIndex
        )) {
            log.debug("💀 DEATH CROSS - Closing position at index {}", currentIndex);
            return new TradingDecision.ClosePosition(
                state.getCurrentPositionId(),
                primaryCandles.get(currentIndex).close()
            );
        }

        return new TradingDecision.Hold();
    }

    private TradingDecision checkTakeProfitLevels(BigDecimal currentPrice) {
        List<BigDecimal> dynamicTpPrices = state.getDynamicTpPrices();

        for (int i = 0; i < dynamicTpPrices.size(); i++) {
            if (state.isTpLevelHit(i)) {
                continue; // Already hit
            }

            BigDecimal tpPrice = dynamicTpPrices.get(i);

            if (currentPrice.compareTo(tpPrice) >= 0) {
                // Calculate quantity to close (equal portions)
                BigDecimal portionSize = BigDecimal.ONE.divide(
                    BigDecimal.valueOf(config.getTargetProfitPct().size()),
                    8,
                    RoundingMode.HALF_UP
                );

                BigDecimal quantityToClose = state.getInitialQuantity().multiply(portionSize);

                // Last TP closes everything remaining
                if (i == dynamicTpPrices.size() - 1) {
                    quantityToClose = state.getRemainingQuantity();
                }

                String reason = String.format("TP%d (%.1f%%)", i + 1, config.getTargetProfitPct().get(i));

                log.debug("🎯 TAKE PROFIT {} - Closing {}/{} at {} (target: {})",
                    reason, quantityToClose, state.getInitialQuantity(), currentPrice, tpPrice);

                state.markTpLevelHit(i);
                state.reduceQuantity(quantityToClose);

                // Return partial close decision
                return new TradingDecision.ClosePositionPartial(
                    state.getCurrentPositionId(),
                    quantityToClose,
                    currentPrice,
                    reason
                );
            }
        }

        return new TradingDecision.Hold();
    }

    private void updateTrailingStopLogic(BigDecimal currentPrice) {
        BigDecimal profitPct = currentPrice.subtract(state.getEntryPrice())
            .divide(state.getEntryPrice(), 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        // ACTIVATE trailing stop
        if (!state.isTrailingStopActive() &&
            profitPct.compareTo(config.getTrailingStopActivationPct()) >= 0) {

            BigDecimal trailingPrice = currentPrice.multiply(
                BigDecimal.ONE.subtract(config.getTrailingStopDistancePct().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            );

            state.setTrailingStopPrice(trailingPrice);
            state.setTrailingStopActive(true);

            log.debug("🔔 Trailing stop ACTIVATED at {} (distance: {}%, activation profit: {}%)",
                trailingPrice, config.getTrailingStopDistancePct(), profitPct);
        }

        // UPDATE trailing stop (only moves up, never down)
        if (state.isTrailingStopActive()) {
            BigDecimal newTrailingPrice = currentPrice.multiply(
                BigDecimal.ONE.subtract(config.getTrailingStopDistancePct().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            );

            if (newTrailingPrice.compareTo(state.getTrailingStopPrice()) > 0) {
                state.setTrailingStopPrice(newTrailingPrice);
                log.debug("Trailing stop updated to {}", newTrailingPrice);

                // HYBRYDOWY TRAILING - dynamically adjust TP levels
                adjustTpLevelsWithTrailing(newTrailingPrice);
            }
        }
    }

    /**
     * HYBRYDOWY TRAILING STOP - Dynamic TP Adjustment.
     *
     * When trailing stop price exceeds a TP level, move that TP higher
     * to lock in more profit while allowing further upside.
     */
    private void adjustTpLevelsWithTrailing(BigDecimal trailingPrice) {
        List<BigDecimal> dynamicTpPrices = state.getDynamicTpPrices();
        BigDecimal buffer = BigDecimal.valueOf(1.10); // 10% buffer above trailing

        for (int i = 0; i < dynamicTpPrices.size(); i++) {
            if (state.isTpLevelHit(i)) {
                continue; // Already hit, don't adjust
            }

            BigDecimal currentTpPrice = dynamicTpPrices.get(i);

            // If trailing stop has exceeded this TP level, move TP higher
            if (trailingPrice.compareTo(currentTpPrice) >= 0) {
                BigDecimal newTpPrice = trailingPrice.multiply(buffer);
                state.updateDynamicTpPrice(i, newTpPrice);

                log.debug("📈 DYNAMIC TP ADJUSTMENT - TP{} moved from {} to {} (trailing: {})",
                    i + 1, currentTpPrice, newTpPrice, trailingPrice);
            }
        }
    }

    // ==================== HELPER METHODS ====================

    private BigDecimal calculatePositionSize(Portfolio portfolio, BigDecimal currentPrice) {
        BigDecimal baseCapital = config.isUseAvailableEquity()
            ? portfolio.getEquity(currentPrice)
            : initialBalance;

        BigDecimal positionValue = baseCapital.multiply(
            config.getPositionSizePct().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
        );

        BigDecimal quantity = positionValue.divide(currentPrice, 8, RoundingMode.HALF_UP);

        log.debug("Position size calculated: {} ({}% of {})", quantity, config.getPositionSizePct(), baseCapital);

        return quantity;
    }

    private List<SwingPoint> getRecentSwingLows(int currentIndex) {
        List<SwingPoint> allSwings = state.getSwingLowsPrimary();

        // Get swings within lookback window
        int fromIndex = Math.max(0, currentIndex - config.getHigherLowsLookback());
        int toIndex = currentIndex;

        return SwingDetector.getSwingLowsInRange(allSwings, fromIndex, toIndex);
    }

    private int getSecondaryIndexForPrimary(int primaryIndex) {
        // Map primary candle index to secondary candle index
        int ratio = config.getPrimaryTimeframe().getRatioTo(config.getSecondaryTimeframe());

        int secondaryIndex = primaryIndex / ratio;

        if (secondaryIndex >= secondaryCandles.size()) {
            return -1; // Not enough secondary data yet
        }

        return secondaryIndex;
    }

    @Override
    public void onPositionClosed(ClosedPosition closedPosition) {
        if (closedPosition.isPartialClose()) {
            log.debug("Partial close processed: {} remaining",
                state.getRemainingQuantity());
        } else {
            log.debug("Position fully closed. Profit: {} ({}%)",
                closedPosition.getRealizedProfit(),
                closedPosition.getRealizedProfitPct());
            state.resetPosition();
        }
    }
}
