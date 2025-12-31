package com.tradingbot.algorithms.smartdca;

import com.tradingbot.core.algorithms.AlgorithmState;
import com.tradingbot.core.algorithms.TradingAlgorithm;
import com.tradingbot.core.algorithms.TradingDecision;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.ClosedPosition;
import com.tradingbot.core.models.OrderSide;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.core.models.Position;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Smart Opportunistic DCA Algorithm for long-term BTC accumulation.
 *
 * Strategy:
 * - Buy dips based on RSI, price drops, and EMA support
 * - Progressive position sizing (3 tiers)
 * - Multi-layered profit management: staged exits, technical exits, trailing stops
 * - Reinvest all profits back into DCA pool
 * - Max drawdown protection (pause buying at -20%)
 *
 * Position Model:
 * - One logical DCA position that grows with each buy (weighted average entry)
 * - Closes percentage of total position based on profit targets
 * - All profit calculated from average entry price
 */
@Slf4j
public class SmartOpportunisticDCAAlgorithm implements TradingAlgorithm<SmartDCAConfig> {

    private final SmartDCAConfig config;
    private final BuySignalEvaluator buyEvaluator;
    private final ProfitManager profitManager;

    // Candle history for indicator calculation
    private final List<Candle> candleHistory;

    // Single position tracking (futures model)
    private String currentPositionId;  // null = no position
    private PositionExitState exitState;  // Exit strategy state

    // Last buy tracking (for cooldown)
    private long lastBuyTimestamp;
    private BigDecimal lastBuyPrice;

    // Peak equity tracking for drawdown
    private BigDecimal peakEquity;

    // Drawdown pause state
    private boolean buyingPaused;
    private long pauseStartTimestamp;
    private BigDecimal priceAtPause;

    public SmartOpportunisticDCAAlgorithm(SmartDCAConfig config) {
        this.config = config;
        this.config.validate();

        this.buyEvaluator = new BuySignalEvaluator(config);
        this.profitManager = new ProfitManager(config);

        this.candleHistory = new ArrayList<>();
        this.currentPositionId = null;
        this.exitState = new PositionExitState();

        this.lastBuyTimestamp = 0;
        this.lastBuyPrice = BigDecimal.ZERO;

        this.peakEquity = config.getStartingCapital();
        this.buyingPaused = false;
        this.pauseStartTimestamp = 0;
        this.priceAtPause = BigDecimal.ZERO;
    }

    @Override
    public String getName() {
        return "SmartOpportunisticDCA";
    }

    @Override
    public SmartDCAConfig getConfig() {
        return config;
    }

    @Override
    public void initialize(BigDecimal initialPrice) {
        log.info("SmartOpportunisticDCA initialized: capital={}, asset={}",
            config.getStartingCapital(), config.getAsset());
        log.info("Buy conditions: RSI < {}, price drop min {}%",
            config.getBuyConditions().getRsi().getOversoldThreshold(),
            config.getBuyConditions().getPriceDrop().getMinFromLastBuyPct());
        log.info("Position tiers: {}", config.getPositionSizing().getTiers().size());
    }

    @Override
    public TradingDecision onCandle(Candle candle, Portfolio portfolio) {
        // Add candle to history
        candleHistory.add(candle);

        BigDecimal currentPrice = candle.close();

        // Update equity tracking
        updateEquityTracking(portfolio, currentPrice);

        // Check drawdown pause/resume
        updateDrawdownState(portfolio, currentPrice, candle.timestamp());

        // 1. Check for exit signals if we have a position
        if (currentPositionId != null) {
            Position position = portfolio.getOpenPositions().get(currentPositionId);

            if (position != null) {
                BigDecimal ema200 = TechnicalIndicators.calculateEMAFull(candleHistory,
                    config.getBuyConditions().getEmaSupport().getPeriod());

                List<ProfitManager.ExitSignal> exitSignals = profitManager.evaluateExits(
                    position, exitState, candleHistory, candle, ema200);

                // Process exit signal
                if (!exitSignals.isEmpty()) {
                    ProfitManager.ExitSignal signal = exitSignals.get(0);
                    return executeExit(signal, position, currentPrice);
                }
            } else {
                // Position not found in portfolio - reset tracking
                log.warn("Current position {} not found in portfolio - resetting", currentPositionId);
                currentPositionId = null;
                exitState.reset();
            }
        }

        // 2. Check buy signals (if not paused and have capital)
        if (!buyingPaused && portfolio.getCashBalance().compareTo(BigDecimal.ZERO) > 0) {
            BuySignalEvaluator.BuySignal buySignal = buyEvaluator.evaluate(
                candleHistory,
                candle,
                lastBuyTimestamp,
                lastBuyPrice.compareTo(BigDecimal.ZERO) > 0 ? lastBuyPrice : null
            );

            if (buySignal != null) {
                return executeBuy(buySignal, currentPrice, candle.timestamp(), portfolio);
            }
        }

        return TradingDecision.Hold.INSTANCE;
    }

    /**
     * Execute a buy decision.
     * Returns OpenPosition if no current position, or IncreasePosition if adding to existing.
     */
    private TradingDecision executeBuy(BuySignalEvaluator.BuySignal signal,
                                       BigDecimal price, long timestamp,
                                       Portfolio portfolio) {

        // Get available cash from portfolio
        BigDecimal availableCash = portfolio.getCashBalance();

        // Calculate position size
        BigDecimal sizePct = signal.getTier().getSizePct();
        BigDecimal investAmount = availableCash
            .multiply(sizePct)
            .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);

        // Ensure we don't exceed available capital
        if (investAmount.compareTo(availableCash) > 0) {
            investAmount = availableCash;
        }

        // Calculate quantity
        BigDecimal rawQuantity = investAmount.divide(price, 8, RoundingMode.HALF_UP);

        // Round to exchange precision
        BigDecimal quantity = config.getTradingPair().roundQuantity(rawQuantity);
        BigDecimal roundedPrice = config.getTradingPair().roundPrice(price);

        // Update last buy tracking
        lastBuyTimestamp = timestamp;
        lastBuyPrice = roundedPrice;

        if (currentPositionId == null) {
            // No existing position - open new one
            log.info("BUY (NEW): tier={}, price={}, qty={}, invest={}, RSI={}, drop={}%",
                signal.getTier().getName(), roundedPrice, quantity, investAmount,
                signal.getRsi(), signal.getPriceDrop());

            String metadata = String.format("%s | Entry: %.2f", signal.getTier().getName(), roundedPrice);

            return new TradingDecision.OpenPosition(
                OrderSide.LONG,
                quantity,
                roundedPrice,
                metadata
            );
        } else {
            // Existing position - increase it (futures-style DCA)
            Position currentPosition = portfolio.getOpenPositions().get(currentPositionId);

            if (currentPosition == null) {
                log.error("Current position {} not found - opening new position instead", currentPositionId);
                currentPositionId = null;
                return executeBuy(signal, price, timestamp, portfolio);  // Retry as new position
            }

            // Calculate future weighted average entry
            BigDecimal currentCost = currentPosition.getQuantity().multiply(currentPosition.getEntryPrice());
            BigDecimal newCost = quantity.multiply(roundedPrice);
            BigDecimal totalCost = currentCost.add(newCost);
            BigDecimal totalQty = currentPosition.getQuantity().add(quantity);
            BigDecimal futureAvgEntry = totalCost.divide(totalQty, 8, RoundingMode.HALF_UP);

            log.info("BUY (INCREASE): tier={}, price={}, qty={}, invest={}, RSI={}, drop={}%, current_avg={}, future_avg={}",
                signal.getTier().getName(), roundedPrice, quantity, investAmount,
                signal.getRsi(), signal.getPriceDrop(),
                currentPosition.getEntryPrice(), futureAvgEntry);

            String metadata = String.format("%s | DCA Avg: %.2f", signal.getTier().getName(), futureAvgEntry);

            return new TradingDecision.IncreasePosition(
                currentPositionId,
                quantity,
                roundedPrice,
                metadata
            );
        }
    }

    /**
     * Execute exit decision (simplified - no FIFO needed for single position)
     */
    private TradingDecision executeExit(ProfitManager.ExitSignal signal, Position position, BigDecimal price) {
        BigDecimal percentageToClose = signal.getPercentageToClose();
        boolean isFullClose = percentageToClose.compareTo(new BigDecimal("100")) >= 0;

        // Round price to exchange precision
        BigDecimal roundedPrice = config.getTradingPair().roundPrice(price);

        if (isFullClose) {
            log.info("EXIT (FULL): type={}, reason={}, avg_entry={}, exit_price={}, profit={}%",
                signal.getType(), signal.getReason(),
                position.getEntryPrice(), roundedPrice,
                calculateProfitPct(position.getEntryPrice(), roundedPrice));

            return new TradingDecision.ClosePosition(currentPositionId, roundedPrice);
        } else {
            // Partial close
            BigDecimal rawQuantityToClose = position.getQuantity()
                .multiply(percentageToClose)
                .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);

            // Round quantity to exchange precision
            BigDecimal quantityToClose = config.getTradingPair().roundQuantity(rawQuantityToClose);

            log.info("EXIT (PARTIAL): type={}, close_pct={}%, qty_to_close={}, reason={}, avg_entry={}, exit_price={}, profit={}%",
                signal.getType(), percentageToClose, quantityToClose, signal.getReason(),
                position.getEntryPrice(), roundedPrice,
                calculateProfitPct(position.getEntryPrice(), roundedPrice));

            return new TradingDecision.ClosePositionPartial(
                currentPositionId,
                quantityToClose,
                roundedPrice,
                signal.getReason()
            );
        }
    }

    /**
     * Calculate profit percentage
     */
    private BigDecimal calculateProfitPct(BigDecimal entryPrice, BigDecimal currentPrice) {
        if (entryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(entryPrice)
            .divide(entryPrice, 8, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    /**
     * Update equity tracking for drawdown calculation
     */
    private void updateEquityTracking(Portfolio portfolio, BigDecimal currentPrice) {
        // Use Portfolio's equity calculation (it already includes all open positions)
        // DON'T mix portfolio.getCashBalance() + dcaPosition.getTotalQuantity() - they're different sources!
        BigDecimal currentEquity = portfolio.getEquity(currentPrice);

        // Update peak
        if (currentEquity.compareTo(peakEquity) > 0) {
            peakEquity = currentEquity;
        }
    }

    /**
     * Update drawdown pause/resume state
     */
    private void updateDrawdownState(Portfolio portfolio, BigDecimal currentPrice, long timestamp) {
        if (!config.getRiskManagement().getDrawdownAction().isEnabled()) {
            return;
        }

        // Use portfolio equity instead of tracking separately
        BigDecimal currentEquity = portfolio.getEquity(currentPrice);

        // Calculate drawdown
        BigDecimal drawdown = peakEquity.subtract(currentEquity)
            .divide(peakEquity, 8, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));

        if (!buyingPaused) {
            // Check if should pause
            if (drawdown.compareTo(config.getRiskManagement().getMaxDrawdownPct()) >= 0) {
                buyingPaused = true;
                pauseStartTimestamp = timestamp;
                priceAtPause = currentPrice;
                log.warn("DRAWDOWN PAUSE: drawdown {}% >= threshold {}%, pausing buying for {} days",
                    drawdown, config.getRiskManagement().getMaxDrawdownPct(),
                    config.getRiskManagement().getDrawdownAction().getCooldownDays());
            }
        } else {
            // Check if should resume
            long daysSincePause = (timestamp - pauseStartTimestamp) / (1000L * 60 * 60 * 24);

            if (daysSincePause >= config.getRiskManagement().getDrawdownAction().getCooldownDays()) {
                // Check price recovery condition
                BigDecimal priceRecovery = currentPrice.subtract(priceAtPause)
                    .divide(priceAtPause, 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

                if (priceRecovery.compareTo(new BigDecimal("5")) >= 0) {
                    buyingPaused = false;
                    log.info("DRAWDOWN RESUME: price recovered +{}%, resuming buying", priceRecovery);
                }
            }
        }
    }

    @Override
    public void onPositionOpened(Position position) {
        // Set or confirm current position ID
        if (currentPositionId == null) {
            currentPositionId = position.getId();
            log.info("Position opened (NEW): ID={}, qty={}, entry={}",
                position.getId().substring(0, 8),
                position.getQuantity(),
                position.getEntryPrice());
        } else {
            // Position increased - currentPositionId should be the same
            if (!currentPositionId.equals(position.getId())) {
                log.warn("Position ID mismatch: current={}, opened={}",
                    currentPositionId.substring(0, 8),
                    position.getId().substring(0, 8));
            }
            log.info("Position increased: ID={}, qty={}, avg_entry={}",
                position.getId().substring(0, 8),
                position.getQuantity(),
                position.getEntryPrice());
        }
    }

    @Override
    public void onPositionClosed(ClosedPosition closedPosition) {
        // Check if position was fully closed by comparing with remaining quantity
        boolean isFullClose = closedPosition.getRemainingQuantity() == null ||
                              closedPosition.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0;

        log.info("Position closed: ID={}, P&L={}, qty_closed={}, remaining={}, full_close={}",
            closedPosition.getId().substring(0, 8),
            closedPosition.getRealizedPnL(),
            closedPosition.getQuantity(),
            closedPosition.getRemainingQuantity(),
            isFullClose);

        if (isFullClose && closedPosition.getId().equals(currentPositionId)) {
            // Full close - reset tracking
            currentPositionId = null;
            exitState.reset();
            log.info("Position fully closed - tracking reset");
        }
    }

    @Override
    public AlgorithmState getState() {
        AlgorithmState state = new AlgorithmState();
        state.setAlgorithmName(getName());
        state.putState("peakEquity", peakEquity);
        state.putState("buyingPaused", buyingPaused);
        state.putState("pauseStartTimestamp", pauseStartTimestamp);
        // Note: currentPositionId and exitState are reconstructed from Portfolio on restore
        return state;
    }

    @Override
    public void restoreState(AlgorithmState state) {
        this.peakEquity = state.getStateOrDefault("peakEquity", config.getStartingCapital());
        this.buyingPaused = state.getStateOrDefault("buyingPaused", false);
        this.pauseStartTimestamp = state.getStateOrDefault("pauseStartTimestamp", 0L);
        // Note: currentPositionId and exitState are reconstructed from Portfolio on restore
    }
}
