package com.tradingbot.algorithms.opportunisticdca;

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
 * Opportunistic DCA Algorithm - Simple RSI-based DCA strategy.
 *
 * Strategy:
 * - Buy on RSI dips (3 configurable tiers)
 * - Sell on profit targets (3 configurable levels)
 * - 24h cooldown between buys (reset on any TP)
 * - Single position model (futures-style)
 */
@Slf4j
public class OpportunisticDCAAlgorithm implements TradingAlgorithm<OpportunisticDCAConfig> {

    // ⚠️ ORDER VALIDATION CONSTANTS
    private static final BigDecimal MINIMUM_ORDER_VALUE_USD = new BigDecimal("10.0");
    private static final BigDecimal MINIMUM_CASH_RESERVE_USD = new BigDecimal("50.0");

    private final OpportunisticDCAConfig config;

    // Candle history for RSI calculation
    private final List<Candle> candleHistory;

    // Single position tracking (futures model)
    private String currentPositionId;  // null = no position

    // Last buy tracking (for cooldown)
    private long lastBuyTimestamp;

    // Tracking which sell levels have been triggered
    private final List<Integer> triggeredSellLevels;

    public OpportunisticDCAAlgorithm(OpportunisticDCAConfig config) {
        this.config = config;
        this.config.validate();

        this.candleHistory = new ArrayList<>();
        this.currentPositionId = null;
        this.lastBuyTimestamp = 0;
        this.triggeredSellLevels = new ArrayList<>();
    }

    @Override
    public String getName() {
        return "OpportunisticDCA";
    }

    @Override
    public OpportunisticDCAConfig getConfig() {
        return config;
    }

    @Override
    public void initialize(Portfolio portfolio) {
        log.info("Initializing {} algorithm", getName());
        log.info("Config: {}", config.getConfigId());
        log.info("Buy tiers: {}", config.getBuyTiers().size());
        log.info("Sell levels: {}", config.getSellLevels().size());
        log.info("Cooldown: {}h", config.getCooldownHours());
    }

    @Override
    public TradingDecision onCandle(Candle candle, Portfolio portfolio) {
        // Add candle to history
        candleHistory.add(candle);

        BigDecimal currentPrice = candle.close();
        long currentTimestamp = candle.timestamp();

        // Need at least 14 candles for RSI calculation
        if (candleHistory.size() < 14) {
            return TradingDecision.Hold.INSTANCE;
        }

        // Calculate RSI
        BigDecimal rsi = calculateRSI(14);
        if (rsi == null) {
            return TradingDecision.Hold.INSTANCE;
        }

        // 1. Check for sell signals if we have a position
        if (currentPositionId != null) {
            Position position = portfolio.getOpenPositions().get(currentPositionId);

            if (position != null) {
                TradingDecision sellDecision = checkSellSignals(position, currentPrice);
                if (sellDecision != null) {
                    return sellDecision;
                }
            } else {
                // Position not found - reset tracking
                log.warn("Position {} not found - resetting", currentPositionId);
                currentPositionId = null;
                triggeredSellLevels.clear();
            }
        }

        // 2. Check buy signals (if cooldown passed and have capital)
        if (isCooldownPassed(currentTimestamp) && portfolio.getCashBalance().compareTo(MINIMUM_CASH_RESERVE_USD) > 0) {
            TradingDecision buyDecision = checkBuySignals(rsi, currentPrice, currentTimestamp, portfolio);
            if (buyDecision != null) {
                return buyDecision;
            }
        }

        return TradingDecision.Hold.INSTANCE;
    }

    /**
     * Check if cooldown period has passed since last buy
     */
    private boolean isCooldownPassed(long currentTimestamp) {
        if (lastBuyTimestamp == 0) {
            return true;  // No previous buy
        }

        long hoursSinceLastBuy = (currentTimestamp - lastBuyTimestamp) / (1000L * 60 * 60);
        return hoursSinceLastBuy >= config.getCooldownHours();
    }

    /**
     * Check buy signals based on RSI tiers
     */
    private TradingDecision checkBuySignals(BigDecimal rsi, BigDecimal price, long timestamp, Portfolio portfolio) {
        // Find the most aggressive tier that matches (lowest RSI threshold)
        OpportunisticDCAConfig.BuyTier matchedTier = null;

        for (OpportunisticDCAConfig.BuyTier tier : config.getBuyTiers()) {
            if (rsi.compareTo(new BigDecimal(tier.getRsiThreshold())) < 0) {
                // RSI below threshold - this tier matches
                if (matchedTier == null || tier.getRsiThreshold() < matchedTier.getRsiThreshold()) {
                    matchedTier = tier;
                }
            }
        }

        if (matchedTier == null) {
            return null;  // No tier matches
        }

        return executeBuy(matchedTier, price, timestamp, portfolio, rsi);
    }

    /**
     * Execute buy decision
     */
    private TradingDecision executeBuy(OpportunisticDCAConfig.BuyTier tier, BigDecimal price,
                                       long timestamp, Portfolio portfolio, BigDecimal rsi) {

        BigDecimal totalCash = portfolio.getCashBalance();

        // ⚠️ VALIDATION #1: Check cash reserve
        BigDecimal availableCash = totalCash.subtract(MINIMUM_CASH_RESERVE_USD);
        if (availableCash.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("BUY REJECTED: Insufficient cash after reserve");
            return null;
        }

        // Calculate position size
        BigDecimal investAmount = availableCash
            .multiply(tier.getSizePct())
            .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);

        // Ensure we don't exceed available capital
        if (investAmount.compareTo(availableCash) > 0) {
            investAmount = availableCash;
        }

        // Round price to exchange precision
        BigDecimal roundedPrice = config.getTradingPair().roundPrice(price);

        // Calculate quantity
        BigDecimal rawQuantity = investAmount.divide(roundedPrice, 8, RoundingMode.HALF_UP);
        BigDecimal quantity = config.getTradingPair().roundQuantity(rawQuantity);

        // ⚠️ VALIDATION #2: Check if quantity is too small
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("BUY REJECTED: Quantity too small (raw={}, rounded={})", rawQuantity, quantity);
            return null;
        }

        // ⚠️ VALIDATION #3: Check minimum order value
        BigDecimal orderValue = quantity.multiply(roundedPrice);
        if (orderValue.compareTo(MINIMUM_ORDER_VALUE_USD) < 0) {
            log.warn("BUY REJECTED: Order value too small ({})", orderValue);
            return null;
        }

        // ⚠️ VALIDATION #4: Verify sufficient cash
        if (orderValue.compareTo(totalCash) > 0) {
            log.warn("BUY REJECTED: Insufficient cash (need={}, have={})", orderValue, totalCash);
            return null;
        }

        // Update last buy timestamp
        lastBuyTimestamp = timestamp;

        if (currentPositionId == null) {
            // Open new position
            log.info("BUY (NEW): RSI={}, threshold={}, size={}%, qty={}, price={}, invest={}",
                rsi, tier.getRsiThreshold(), tier.getSizePct(), quantity, roundedPrice, orderValue);

            return new TradingDecision.OpenPosition(
                OrderSide.LONG,
                quantity,
                roundedPrice,
                String.format("RSI %.2f < %d", rsi, tier.getRsiThreshold())
            );
        } else {
            // Increase existing position
            log.info("BUY (INCREASE): RSI={}, threshold={}, size={}%, qty={}, price={}, invest={}",
                rsi, tier.getRsiThreshold(), tier.getSizePct(), quantity, roundedPrice, orderValue);

            return new TradingDecision.IncreasePosition(
                currentPositionId,
                quantity,
                roundedPrice,
                String.format("RSI %.2f < %d", rsi, tier.getRsiThreshold())
            );
        }
    }

    /**
     * Check sell signals based on profit levels
     */
    private TradingDecision checkSellSignals(Position position, BigDecimal currentPrice) {
        BigDecimal profitPct = calculateProfitPct(position.getEntryPrice(), currentPrice);

        // Check each sell level in order
        for (int i = 0; i < config.getSellLevels().size(); i++) {
            OpportunisticDCAConfig.SellLevel level = config.getSellLevels().get(i);

            // Skip if already triggered
            if (triggeredSellLevels.contains(i)) {
                continue;
            }

            // Check if profit threshold reached
            if (profitPct.compareTo(level.getProfitPct()) >= 0) {
                // Mark as triggered
                triggeredSellLevels.add(i);

                return executeSell(level, position, currentPrice, profitPct);
            }
        }

        return null;
    }

    /**
     * Execute sell decision
     */
    private TradingDecision executeSell(OpportunisticDCAConfig.SellLevel level, Position position,
                                        BigDecimal price, BigDecimal profitPct) {

        BigDecimal roundedPrice = config.getTradingPair().roundPrice(price);
        boolean isFullClose = level.getClosePct().compareTo(new BigDecimal("100")) >= 0;

        if (isFullClose) {
            log.info("SELL (FULL): profit={:.2f}% >= {:.2f}%, closing 100%",
                profitPct, level.getProfitPct());

            return new TradingDecision.ClosePosition(currentPositionId, roundedPrice);
        } else {
            // Partial close
            BigDecimal rawQuantity = position.getQuantity()
                .multiply(level.getClosePct())
                .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);

            BigDecimal quantity = config.getTradingPair().roundQuantity(rawQuantity);

            // ⚠️ VALIDATION: Check if quantity is too small
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("SELL SKIPPED: Quantity too small (raw={}, rounded={})", rawQuantity, quantity);
                return null;
            }

            // ⚠️ VALIDATION: Check if trying to close more than available
            if (quantity.compareTo(position.getQuantity()) > 0) {
                log.warn("SELL ADJUSTED: Trying to close {} but only {} available - closing all",
                    quantity, position.getQuantity());
                return new TradingDecision.ClosePosition(currentPositionId, roundedPrice);
            }

            log.info("SELL (PARTIAL): profit={:.2f}% >= {:.2f}%, closing {:.0f}% (qty={})",
                profitPct, level.getProfitPct(), level.getClosePct(), quantity);

            return new TradingDecision.ClosePositionPartial(
                currentPositionId,
                quantity,
                roundedPrice,
                String.format("TP: profit %.2f%%", profitPct)
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
     * Calculate RSI indicator
     */
    private BigDecimal calculateRSI(int period) {
        if (candleHistory.size() < period + 1) {
            return null;
        }

        List<BigDecimal> gains = new ArrayList<>();
        List<BigDecimal> losses = new ArrayList<>();

        // Calculate price changes
        for (int i = candleHistory.size() - period; i < candleHistory.size(); i++) {
            BigDecimal currentClose = candleHistory.get(i).close();
            BigDecimal previousClose = candleHistory.get(i - 1).close();
            BigDecimal change = currentClose.subtract(previousClose);

            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gains.add(change);
                losses.add(BigDecimal.ZERO);
            } else {
                gains.add(BigDecimal.ZERO);
                losses.add(change.abs());
            }
        }

        // Calculate average gain and loss
        BigDecimal avgGain = gains.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);

        BigDecimal avgLoss = losses.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("100");  // No losses = RSI 100
        }

        // Calculate RS and RSI
        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        BigDecimal rsi = new BigDecimal("100").subtract(
            new BigDecimal("100").divide(
                BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP
            )
        );

        return rsi;
    }

    @Override
    public void onPositionOpened(Position position) {
        if (currentPositionId == null) {
            currentPositionId = position.getId();
            log.info("Position opened (NEW): ID={}, qty={}, entry={}",
                position.getId().substring(0, 8),
                position.getQuantity(),
                position.getEntryPrice());
        } else {
            log.info("Position increased: ID={}, qty={}, avg_entry={}",
                position.getId().substring(0, 8),
                position.getQuantity(),
                position.getEntryPrice());
        }
    }

    @Override
    public void onPositionClosed(ClosedPosition closedPosition) {
        boolean isFullClose = closedPosition.getRemainingQuantity() == null ||
                              closedPosition.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0;

        log.info("Position closed: ID={}, P&L={}, qty_closed={}, remaining={}, full={}",
            closedPosition.getId().substring(0, 8),
            closedPosition.getRealizedPnL(),
            closedPosition.getQuantity(),
            closedPosition.getRemainingQuantity(),
            isFullClose);

        if (isFullClose && closedPosition.getId().equals(currentPositionId)) {
            // Full close - reset tracking
            currentPositionId = null;
            triggeredSellLevels.clear();
            log.info("Position fully closed - tracking reset");
        }

        // ⚠️ IMPORTANT: Reset cooldown on ANY TP execution
        log.info("TP executed - resetting cooldown");
        lastBuyTimestamp = 0;
    }

    @Override
    public AlgorithmState getState() {
        AlgorithmState state = new AlgorithmState();
        state.setAlgorithmName(getName());
        state.putState("lastBuyTimestamp", lastBuyTimestamp);
        return state;
    }

    @Override
    public void restoreState(AlgorithmState state) {
        this.lastBuyTimestamp = state.getStateOrDefault("lastBuyTimestamp", 0L);
    }
}
