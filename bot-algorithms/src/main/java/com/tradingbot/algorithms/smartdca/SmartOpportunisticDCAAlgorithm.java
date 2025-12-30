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

    // DCA position tracking (one logical position)
    private DCAPosition dcaPosition;

    // Peak equity tracking for drawdown
    private BigDecimal peakEquity;

    // Drawdown pause state
    private boolean buyingPaused;
    private long pauseStartTimestamp;
    private BigDecimal priceAtPause;

    // Pending exit state (for closing across multiple positions)
    private BigDecimal pendingExitQuantity;
    private BigDecimal pendingExitPercentage;
    private String pendingExitReason;

    public SmartOpportunisticDCAAlgorithm(SmartDCAConfig config) {
        this.config = config;
        this.config.validate();

        this.buyEvaluator = new BuySignalEvaluator(config);
        this.profitManager = new ProfitManager(config);

        this.candleHistory = new ArrayList<>();
        this.dcaPosition = new DCAPosition();

        this.peakEquity = config.getStartingCapital();
        this.buyingPaused = false;
        this.pauseStartTimestamp = 0;
        this.priceAtPause = BigDecimal.ZERO;

        this.pendingExitQuantity = BigDecimal.ZERO;
        this.pendingExitPercentage = BigDecimal.ZERO;
        this.pendingExitReason = null;
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

        // 1. Handle pending exits first (close positions in FIFO order)
        if (pendingExitQuantity.compareTo(BigDecimal.ZERO) > 0 && !dcaPosition.isEmpty()) {
            return processPendingExit(portfolio, currentPrice);
        }

        // 2. Check for new exit signals
        if (!dcaPosition.isEmpty()) {
            BigDecimal ema200 = TechnicalIndicators.calculateEMAFull(candleHistory,
                config.getBuyConditions().getEmaSupport().getPeriod());

            List<ProfitManager.ExitSignal> exitSignals = profitManager.evaluateExits(
                dcaPosition, candleHistory, candle, ema200);

            // Process exit signals (initiate pending exit)
            if (!exitSignals.isEmpty()) {
                return initiatePendingExit(exitSignals.get(0), currentPrice, portfolio);
            }
        }

        // 3. Check buy signals (if not paused and have capital)
        if (!buyingPaused && portfolio.getCashBalance().compareTo(BigDecimal.ZERO) > 0) {
            BuySignalEvaluator.BuySignal buySignal = buyEvaluator.evaluate(
                candleHistory,
                candle,
                dcaPosition.getLastBuyTimestamp(),
                dcaPosition.getLastBuyPrice().compareTo(BigDecimal.ZERO) > 0
                    ? dcaPosition.getLastBuyPrice()
                    : null
            );

            if (buySignal != null) {
                return executeBuy(buySignal, currentPrice, candle.timestamp(), portfolio);
            }
        }

        return TradingDecision.Hold.INSTANCE;
    }

    /**
     * Execute a buy decision
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
        BigDecimal quantity = investAmount.divide(price, 8, RoundingMode.HALF_UP);

        // Check max concurrent positions
        if (dcaPosition.getPositionCount() >= config.getRiskManagement().getMaxConcurrentPositions()) {
            log.warn("Max concurrent positions reached: {}", config.getRiskManagement().getMaxConcurrentPositions());
            return TradingDecision.Hold.INSTANCE;
        }

        log.info("BUY: tier={}, price={}, qty={}, invest={}, RSI={}, drop={}%, current_avg_entry={}",
            signal.getTier().getName(), price, quantity, investAmount,
            signal.getRsi(), signal.getPriceDrop(),
            dcaPosition.isEmpty() ? "N/A" : dcaPosition.getAvgEntryPrice());

        // NOTE: Don't modify capital here - Portfolio.addPosition() will deduct cashBalance

        // Calculate what the new weighted average entry will be after this buy
        BigDecimal futureAvgEntry = dcaPosition.getTotalInvested().add(investAmount)
            .divide(dcaPosition.getTotalQuantity().add(quantity), 8, RoundingMode.HALF_UP);

        // Return decision to open position
        // Store tier name and DCA weighted average in metadata for display
        String metadata = String.format("%s | DCA Avg: %.2f", signal.getTier().getName(), futureAvgEntry);

        return new TradingDecision.OpenPosition(
            OrderSide.LONG,
            quantity,
            price,
            metadata
        );
    }

    /**
     * Initiate a pending exit (sets up state for multi-position closing)
     */
    private TradingDecision initiatePendingExit(ProfitManager.ExitSignal signal, BigDecimal price, Portfolio portfolio) {
        // Calculate total quantity to close
        BigDecimal totalQtyToClose = dcaPosition.getTotalQuantity()
            .multiply(signal.getPercentageToClose())
            .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);

        // Set pending exit state
        pendingExitQuantity = totalQtyToClose;
        pendingExitPercentage = signal.getPercentageToClose();
        pendingExitReason = signal.getReason();

        log.info("EXIT INITIATED: type={}, close_pct={}%, reason={}, total_qty_to_close={}, avg_entry={}",
            signal.getType(), signal.getPercentageToClose(), signal.getReason(),
            totalQtyToClose, dcaPosition.getAvgEntryPrice());

        // Process first close immediately
        return processPendingExit(portfolio, price);
    }

    /**
     * Process pending exit by closing positions in FIFO order
     */
    private TradingDecision processPendingExit(Portfolio portfolio, BigDecimal price) {
        // Get oldest position ID
        String oldestPositionId = dcaPosition.getOldestPositionId();

        if (oldestPositionId == null) {
            log.error("No position ID found but pendingExitQuantity > 0 - resetting pending state");
            pendingExitQuantity = BigDecimal.ZERO;
            pendingExitReason = null;
            return TradingDecision.Hold.INSTANCE;
        }

        // Get the actual position from portfolio to know its quantity
        Position oldestPosition = portfolio.getOpenPositions().get(oldestPositionId);

        if (oldestPosition == null) {
            log.error("Position {} not found in portfolio - resetting pending state", oldestPositionId);
            pendingExitQuantity = BigDecimal.ZERO;
            pendingExitReason = null;
            return TradingDecision.Hold.INSTANCE;
        }

        // Determine how much to close from this position
        BigDecimal qtyToCloseFromThisPosition;
        boolean closeFullPosition;

        BigDecimal availableQty = oldestPosition.getQuantity();

        if (pendingExitQuantity.compareTo(availableQty) >= 0) {
            // Close entire position
            qtyToCloseFromThisPosition = availableQty;
            closeFullPosition = true;
        } else {
            // Close partial
            qtyToCloseFromThisPosition = pendingExitQuantity;
            closeFullPosition = false;
        }

        // Calculate P&L and update DCAPosition proportionally
        BigDecimal percentageOfTotal = qtyToCloseFromThisPosition
            .divide(dcaPosition.getTotalQuantity(), 8, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));

        DCAPosition.CloseResult result = dcaPosition.closePercentage(percentageOfTotal, price);

        log.info("EXIT EXECUTING: close_qty={}, position_id={}, full={}, P&L={}, remaining_pending={}",
            qtyToCloseFromThisPosition,
            oldestPositionId.substring(0, 8),
            closeFullPosition,
            result.getRealizedPnL(),
            pendingExitQuantity.subtract(qtyToCloseFromThisPosition));

        // NOTE: Portfolio.closePartialPosition() already adds proceeds to cashBalance
        // No need to modify availableCapital here (it would cause double-counting)
        log.debug("P&L from close: {}, invested_recovered={}, total_proceeds={}",
            result.getRealizedPnL(), result.getInvestedClosed(),
            result.getRealizedPnL().add(result.getInvestedClosed()));

        // Reduce pending exit quantity
        pendingExitQuantity = pendingExitQuantity.subtract(qtyToCloseFromThisPosition);

        // Remove position ID if we're closing it fully
        // This prevents double-removal in onPositionClosed()
        if (closeFullPosition) {
            dcaPosition.removePositionId(oldestPositionId);
            log.debug("Removed position ID {} from DCA tracking (full close)", oldestPositionId.substring(0, 8));
        }

        // Clear pending state if done
        if (pendingExitQuantity.compareTo(new BigDecimal("0.00000001")) < 0) {
            log.info("EXIT COMPLETED: {}%, reason={}", pendingExitPercentage, pendingExitReason);
            pendingExitQuantity = BigDecimal.ZERO;
            pendingExitPercentage = BigDecimal.ZERO;
            pendingExitReason = null;
        }

        // Return close decision
        if (closeFullPosition) {
            return new TradingDecision.ClosePosition(oldestPositionId, price);
        } else {
            return new TradingDecision.ClosePositionPartial(
                oldestPositionId,
                qtyToCloseFromThisPosition,
                price,
                pendingExitReason
            );
        }
    }

    /**
     * Update equity tracking for drawdown calculation
     */
    private void updateEquityTracking(Portfolio portfolio, BigDecimal currentPrice) {
        BigDecimal currentEquity = portfolio.getCashBalance();

        // Add unrealized position value
        if (!dcaPosition.isEmpty()) {
            currentEquity = currentEquity.add(
                dcaPosition.getTotalQuantity().multiply(currentPrice)
            );
        }

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
        // Add purchase to DCA position
        dcaPosition.addPurchase(
            position.getQuantity(),
            position.getEntryPrice(),
            position.getOpenTimestamp(),
            position.getId()
        );

        log.debug("Position opened: ID={}, qty={}, price={}, new_avg_entry={}, total_qty={}, position_count={}",
            position.getId().substring(0, 8),
            position.getQuantity(),
            position.getEntryPrice(),
            dcaPosition.getAvgEntryPrice(),
            dcaPosition.getTotalQuantity(),
            dcaPosition.getPositionCount());
    }

    @Override
    public void onPositionClosed(ClosedPosition closedPosition) {
        // NOTE: Position ID removal is handled in processPendingExit() when we know it's a full close.
        // This callback is just for logging and metrics.

        log.debug("Position close callback: ID={}, P&L={}, qty={}, current_dca_positions={}",
            closedPosition.getId().substring(0, 8),
            closedPosition.getRealizedPnL(),
            closedPosition.getQuantity(),
            dcaPosition.getPositionCount());
    }

    @Override
    public AlgorithmState getState() {
        AlgorithmState state = new AlgorithmState();
        state.setAlgorithmName(getName());
        state.putState("peakEquity", peakEquity);
        state.putState("buyingPaused", buyingPaused);
        state.putState("pauseStartTimestamp", pauseStartTimestamp);
        // Note: Full DCAPosition serialization would need custom logic
        // Note: availableCapital removed - use portfolio.getCashBalance() instead
        return state;
    }

    @Override
    public void restoreState(AlgorithmState state) {
        this.peakEquity = state.getStateOrDefault("peakEquity", config.getStartingCapital());
        this.buyingPaused = state.getStateOrDefault("buyingPaused", false);
        this.pauseStartTimestamp = state.getStateOrDefault("pauseStartTimestamp", 0L);
        // Note: Full DCAPosition restoration would need custom logic
        // Note: availableCapital removed - use portfolio.getCashBalance() instead
    }
}
