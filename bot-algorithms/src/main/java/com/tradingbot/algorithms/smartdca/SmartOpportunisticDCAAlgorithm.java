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
 * - Buy dips based on RSI, price drops, volume spikes, and EMA support
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

    // Available capital (increases with reinvestment)
    private BigDecimal availableCapital;

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
        this.dcaPosition = new DCAPosition();

        this.availableCapital = config.getStartingCapital();
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
        log.info("Buy conditions: RSI < {}, price drop min {}%, volume spike {}x",
            config.getBuyConditions().getRsi().getOversoldThreshold(),
            config.getBuyConditions().getPriceDrop().getMinFromLastBuyPct(),
            config.getBuyConditions().getVolume().getSpikeMultiplier());
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
        updateDrawdownState(currentPrice, candle.timestamp());

        // 1. Check exit signals first
        if (!dcaPosition.isEmpty()) {
            BigDecimal ema200 = TechnicalIndicators.calculateEMAFull(candleHistory,
                config.getBuyConditions().getEmaSupport().getPeriod());

            List<ProfitManager.ExitSignal> exitSignals = profitManager.evaluateExits(
                dcaPosition, candleHistory, candle, ema200);

            // Process exit signals (take first one if multiple)
            if (!exitSignals.isEmpty()) {
                return executeExit(exitSignals.get(0), currentPrice, candle.timestamp());
            }
        }

        // 2. Check buy signals (if not paused and have capital)
        if (!buyingPaused && availableCapital.compareTo(BigDecimal.ZERO) > 0) {
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

        // Calculate position size
        BigDecimal sizePct = signal.getTier().getSizePct();
        BigDecimal investAmount = availableCapital
            .multiply(sizePct)
            .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);

        // Ensure we don't exceed available capital
        if (investAmount.compareTo(availableCapital) > 0) {
            investAmount = availableCapital;
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

        // Deduct from available capital now (before position opens)
        availableCapital = availableCapital.subtract(investAmount);

        // Return decision to open position
        // We'll add to dcaPosition in onPositionOpened callback
        return new TradingDecision.OpenPosition(
            OrderSide.LONG,
            quantity,
            price,
            signal.getTier().getName()  // Store tier name in metadata
        );
    }

    /**
     * Execute an exit decision
     */
    private TradingDecision executeExit(ProfitManager.ExitSignal signal,
                                        BigDecimal price, long timestamp) {

        // Calculate quantity to close
        DCAPosition.CloseResult result = dcaPosition.closePercentage(
            signal.getPercentageToClose(), price);

        log.info("EXIT: type={}, close_pct={}%, reason={}, qty={}, P&L={}, avg_entry={}",
            signal.getType(), signal.getPercentageToClose(), signal.getReason(),
            result.getQuantityClosed(), result.getRealizedPnL(), dcaPosition.getAvgEntryPrice());

        // Reinvest if configured
        if (config.getReinvestment().isEnabled() && config.getReinvestment().isAddToAvailableCapital()) {
            BigDecimal totalReturned = result.getRealizedPnL().add(result.getInvestedClosed());
            availableCapital = availableCapital.add(totalReturned);
            log.info("Reinvested: P&L={}, invested_recovered={}, new_capital={}",
                result.getRealizedPnL(), result.getInvestedClosed(), availableCapital);
        }

        // Get oldest position ID to close
        String oldestPositionId = dcaPosition.getOldestPositionId();

        if (oldestPositionId == null) {
            log.error("No position ID found for closing - this shouldn't happen!");
            return TradingDecision.Hold.INSTANCE;
        }

        // Return partial close decision
        return new TradingDecision.ClosePositionPartial(
            oldestPositionId,
            result.getQuantityClosed(),
            price,
            signal.getReason()
        );
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
    private void updateDrawdownState(BigDecimal currentPrice, long timestamp) {
        if (!config.getRiskManagement().getDrawdownAction().isEnabled()) {
            return;
        }

        BigDecimal currentEquity = availableCapital;
        if (!dcaPosition.isEmpty()) {
            currentEquity = currentEquity.add(
                dcaPosition.getTotalQuantity().multiply(currentPrice)
            );
        }

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
        // Remove position ID from tracking
        dcaPosition.removePositionId(closedPosition.getId());

        log.debug("Position closed: ID={}, P&L={}, remaining_positions={}",
            closedPosition.getId().substring(0, 8),
            closedPosition.getRealizedPnL(),
            dcaPosition.getPositionCount());
    }

    @Override
    public AlgorithmState getState() {
        AlgorithmState state = new AlgorithmState();
        state.setAlgorithmName(getName());
        state.putState("availableCapital", availableCapital);
        state.putState("peakEquity", peakEquity);
        state.putState("buyingPaused", buyingPaused);
        state.putState("pauseStartTimestamp", pauseStartTimestamp);
        // Note: Full DCAPosition serialization would need custom logic
        return state;
    }

    @Override
    public void restoreState(AlgorithmState state) {
        this.availableCapital = state.getStateOrDefault("availableCapital", config.getStartingCapital());
        this.peakEquity = state.getStateOrDefault("peakEquity", config.getStartingCapital());
        this.buyingPaused = state.getStateOrDefault("buyingPaused", false);
        this.pauseStartTimestamp = state.getStateOrDefault("pauseStartTimestamp", 0L);
        // Note: Full DCAPosition restoration would need custom logic
    }
}
