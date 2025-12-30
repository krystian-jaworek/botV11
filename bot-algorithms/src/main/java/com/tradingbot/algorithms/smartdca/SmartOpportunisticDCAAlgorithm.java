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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smart Opportunistic DCA Algorithm for long-term BTC accumulation.
 *
 * Strategy:
 * - Buy dips based on RSI, price drops, volume spikes, and EMA support
 * - Progressive position sizing (3 tiers)
 * - Multi-layered profit management: staged exits, technical exits, trailing stops
 * - Reinvest all profits back into DCA pool
 * - Max drawdown protection (pause buying at -20%)
 */
@Slf4j
public class SmartOpportunisticDCAAlgorithm implements TradingAlgorithm<SmartDCAConfig> {

    private final SmartDCAConfig config;
    private final BuySignalEvaluator buyEvaluator;
    private final ProfitManager profitManager;

    // Candle history for indicator calculation
    private final List<Candle> candleHistory;

    // DCA position tracking
    private DCAPosition dcaPosition;

    // Available capital (increases with reinvestment)
    private BigDecimal availableCapital;

    // Peak equity tracking for drawdown
    private BigDecimal peakEquity;

    // Drawdown pause state
    private boolean buyingPaused;
    private long pauseStartTimestamp;
    private BigDecimal priceAtPause;

    // Track position IDs created by this algorithm
    // Maps internal parcel ID to external Position ID
    private final Map<String, String> parcelToPositionId;

    public SmartOpportunisticDCAAlgorithm(SmartDCAConfig config) {
        this.config = config;
        this.config.validate();

        this.buyEvaluator = new BuySignalEvaluator(config);
        this.profitManager = new ProfitManager(config);

        this.candleHistory = new ArrayList<>();
        this.dcaPosition = new DCAPosition();
        this.parcelToPositionId = new HashMap<>();

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

            // Process exit signals
            for (ProfitManager.ExitSignal signal : exitSignals) {
                return executeExit(signal, currentPrice, candle.timestamp());
            }
        }

        // 2. Check buy signals (if not paused and have capital)
        if (!buyingPaused && availableCapital.compareTo(BigDecimal.ZERO) > 0) {
            BuySignalEvaluator.BuySignal buySignal = buyEvaluator.evaluate(
                candleHistory,
                candle,
                dcaPosition.getLastBuyTimestamp(),
                dcaPosition.isEmpty() ? null : dcaPosition.getParcels().getLast().getEntryPrice()
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
        if (dcaPosition.getParcelCount() >= config.getRiskManagement().getMaxConcurrentPositions()) {
            log.warn("Max concurrent positions reached: {}", config.getRiskManagement().getMaxConcurrentPositions());
            return TradingDecision.Hold.INSTANCE;
        }

        // Create parcel
        DCAParcel parcel = DCAParcel.create(timestamp, price, quantity, investAmount, signal.getTier().getName());

        log.info("BUY: tier={}, price={}, qty={}, invest={}, RSI={}, drop={}%",
            signal.getTier().getName(), price, quantity, investAmount,
            signal.getRsi(), signal.getPriceDrop());

        // We'll add parcel in onPositionOpened callback
        // For now, just return the decision
        return new TradingDecision.OpenPosition(
            OrderSide.LONG,
            quantity,
            price,
            parcel.getId()  // Store parcel ID in metadata
        );
    }

    /**
     * Execute an exit decision
     */
    private TradingDecision executeExit(ProfitManager.ExitSignal signal,
                                        BigDecimal price, long timestamp) {

        log.info("EXIT: type={}, close_pct={}%, reason={}",
            signal.getType(), signal.getPercentageToClose(), signal.getReason());

        // Close percentage of position
        DCAPosition.CloseResult result = dcaPosition.closePercentage(
            signal.getPercentageToClose(), price);

        log.info("Realized P&L: {}, Qty closed: {}, Invested recovered: {}",
            result.getRealizedPnL(), result.getQuantityClosed(), result.getInvestedClosed());

        // Reinvest if configured
        if (config.getReinvestment().isEnabled() && config.getReinvestment().isAddToAvailableCapital()) {
            availableCapital = availableCapital.add(result.getRealizedPnL()).add(result.getInvestedClosed());
            log.info("Reinvested: new available capital = {}", availableCapital);
        }

        // Since we're using aggregate position, we can't directly map to individual Position IDs
        // The backtest engine will need to handle this differently
        // For now, we'll just signal a partial close with the quantity
        // This is a limitation - we may need to adjust the architecture

        // Return a special decision that the engine can interpret
        // We'll use metadata to encode the quantity and reason
        String metadata = String.format("qty:%.8f,reason:%s", result.getQuantityClosed(), signal.getReason());

        // We need to return ClosePositionPartial, but we don't have a positionId
        // This is a design issue - DCA doesn't map 1:1 to Position model
        // For now, we'll use a placeholder and handle in the engine
        return new TradingDecision.ClosePositionPartial(
            "DCA_POSITION",  // Placeholder
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
        // Extract parcel ID from metadata
        String parcelId = position.getMetadata();

        // Create parcel and add to DCA position
        DCAParcel parcel = DCAParcel.builder()
            .id(parcelId)
            .timestamp(position.getOpenTimestamp())
            .entryPrice(position.getEntryPrice())
            .quantity(position.getQuantity())
            .investedAmount(position.getEntryPrice().multiply(position.getQuantity()))
            .tierName("DCA")  // We don't have tier name here, would need to pass it
            .build();

        dcaPosition.addParcel(parcel);

        // Track mapping
        parcelToPositionId.put(parcelId, position.getId());

        // Deduct from available capital
        availableCapital = availableCapital.subtract(parcel.getInvestedAmount());

        log.debug("Position opened: parcel {}, total parcels: {}, avg entry: {}",
            parcelId.substring(0, 8), dcaPosition.getParcelCount(), dcaPosition.getAverageEntryPrice());
    }

    @Override
    public void onPositionClosed(ClosedPosition closedPosition) {
        log.debug("Position closed: {}, P&L: {}", closedPosition.getId(), closedPosition.getRealizedPnL());
    }

    @Override
    public AlgorithmState getState() {
        AlgorithmState state = new AlgorithmState();
        state.setAlgorithmName(getName());
        state.putState("availableCapital", availableCapital);
        state.putState("peakEquity", peakEquity);
        state.putState("buyingPaused", buyingPaused);
        state.putState("pauseStartTimestamp", pauseStartTimestamp);
        // Note: DCAPosition serialization would need custom logic
        return state;
    }

    @Override
    public void restoreState(AlgorithmState state) {
        this.availableCapital = state.getStateOrDefault("availableCapital", config.getStartingCapital());
        this.peakEquity = state.getStateOrDefault("peakEquity", config.getStartingCapital());
        this.buyingPaused = state.getStateOrDefault("buyingPaused", false);
        this.pauseStartTimestamp = state.getStateOrDefault("pauseStartTimestamp", 0L);
        // Note: DCAPosition restoration would need custom logic
    }
}
