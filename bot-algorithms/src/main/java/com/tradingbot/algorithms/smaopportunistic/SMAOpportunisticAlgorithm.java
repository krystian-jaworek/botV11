package com.tradingbot.algorithms.smaopportunistic;

import com.tradingbot.core.algorithms.AlgorithmState;
import com.tradingbot.core.algorithms.TradingAlgorithm;
import com.tradingbot.core.algorithms.TradingDecision;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.ClosedPosition;
import com.tradingbot.core.models.OrderSide;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.core.models.Position;
import com.tradingbot.core.models.TradingPair;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * SMA Opportunistic Algorithm
 *
 * SMA-based DCA strategy with cooldown:
 * 1. Buy when price < SMA - X%
 * 2. Position size: Y% of portfolio
 * 3. Take profit at entry + Z%
 * 4. Cooldown B hours between buys
 * 5. DCA: if cooldown passed and still below SMA - X%, add to position
 */
@Slf4j
public class SMAOpportunisticAlgorithm implements TradingAlgorithm<SMAOpportunisticConfig> {

    private static final BigDecimal MINIMUM_ORDER_VALUE_USD = new BigDecimal("10.0");
    private static final BigDecimal MINIMUM_CASH_RESERVE_USD = new BigDecimal("50.0");

    private final SMAOpportunisticConfig config;
    private final List<Candle> candleHistory;

    // Position tracking (single position model - futures style)
    private String currentPositionId;
    private long lastBuyTimestamp;
    private BigDecimal initialPositionValue;  // USD value of first position (for consistent DCA sizing)

    public SMAOpportunisticAlgorithm(SMAOpportunisticConfig config) {
        this.config = config;
        this.config.validate();
        this.candleHistory = new ArrayList<>();
        this.currentPositionId = null;
        this.lastBuyTimestamp = 0;
        this.initialPositionValue = null;
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
        log.info("Parameters: SMA={}, Size={}%, Deviation={}%, TP={}%, Cooldown={}h",
            config.getSmaPeriod(),
            config.getPositionSizePercent(),
            config.getSmaDeviationPercent(),
            config.getTakeProfitPercent(),
            config.getCooldownHours()
        );
    }

    @Override
    public void onPositionOpened(Position position) {
        // Only update on FIRST open (when currentPositionId is null)
        // Do NOT update on IncreasePosition to avoid resetting cooldown
        if (currentPositionId == null) {
            currentPositionId = position.getId();
            lastBuyTimestamp = position.getOpenTimestamp();
            log.info("Position opened: {} | Entry: {} | Quantity: {}",
                position.getId(), position.getEntryPrice(), position.getQuantity());
        } else {
            log.debug("Position increased: {} | New Entry: {} | Total Quantity: {}",
                position.getId(), position.getEntryPrice(), position.getQuantity());
        }
    }

    @Override
    public void onPositionClosed(ClosedPosition closedPosition) {
        log.info("Position closed: {} | Profit: {}% (${}) | Duration: {} candles",
            closedPosition.getId(),
            closedPosition.getRealizedPnLPercentage(),
            closedPosition.getRealizedPnL(),
            (closedPosition.getCloseTimestamp() - closedPosition.getOpenTimestamp()) / 60000
        );
        currentPositionId = null;
        initialPositionValue = null;  // Reset for next position
        // Note: We DON'T reset lastBuyTimestamp here - cooldown continues
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
        long currentTimestamp = candle.timestamp();

        // Calculate SMA threshold: SMA - X%
        BigDecimal smaThreshold = currentSMA.multiply(
            BigDecimal.ONE.subtract(config.getSmaDeviationPercent().divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP))
        );

        log.debug("Price: {} | SMA: {} | Threshold (SMA-{}%): {} | Below: {}",
            currentPrice, currentSMA, config.getSmaDeviationPercent(), smaThreshold,
            currentPrice.compareTo(smaThreshold) < 0
        );

        // Case 1: NO position - check for entry
        if (currentPositionId == null) {
            return checkEntrySignal(currentPrice, smaThreshold, portfolio, currentTimestamp);
        }

        // Case 2: HAVE position - check TP or DCA
        return checkExitOrDCASignal(currentPrice, smaThreshold, portfolio, currentTimestamp);
    }

    /**
     * Check for entry signal when no position is open
     */
    private TradingDecision checkEntrySignal(BigDecimal currentPrice, BigDecimal smaThreshold,
                                             Portfolio portfolio, long currentTimestamp) {
        // Entry condition: price < SMA - X%
        if (currentPrice.compareTo(smaThreshold) >= 0) {
            return TradingDecision.Hold.INSTANCE;
        }

        log.info("Entry signal: price {} < threshold {} (SMA - {}%)",
            currentPrice, smaThreshold, config.getSmaDeviationPercent());

        // Calculate position size: Y% of portfolio (at time of FIRST entry)
        BigDecimal totalCash = portfolio.getCashBalance();
        BigDecimal positionValue = totalCash
            .multiply(config.getPositionSizePercent())
            .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);

        // Store initial position value for consistent DCA sizing
        initialPositionValue = positionValue;

        // Validate order
        TradingDecision decision = validateAndCreateBuyOrder(positionValue, currentPrice, totalCash, currentTimestamp);
        if (decision != null) {
            return decision;
        }

        return TradingDecision.Hold.INSTANCE;
    }

    /**
     * Check for exit (TP) or DCA signal when position is open
     */
    private TradingDecision checkExitOrDCASignal(BigDecimal currentPrice, BigDecimal smaThreshold,
                                                  Portfolio portfolio, long currentTimestamp) {
        // Get current position
        Position position = portfolio.getOpenPositions().get(currentPositionId);
        if (position == null) {
            log.warn("Position {} not found - resetting", currentPositionId);
            currentPositionId = null;
            return TradingDecision.Hold.INSTANCE;
        }

        // Check Take Profit: price >= entry + Z%
        BigDecimal tpPrice = position.getEntryPrice().multiply(
            BigDecimal.ONE.add(config.getTakeProfitPercent().divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP))
        );

        if (currentPrice.compareTo(tpPrice) >= 0) {
            log.info("TP triggered: price {} >= TP price {} (entry + {}%)",
                currentPrice, tpPrice, config.getTakeProfitPercent());
            return new TradingDecision.ClosePosition(currentPositionId, currentPrice);
        }

        // Check DCA conditions:
        // 1. Cooldown period passed
        // 2. Price still below SMA - X%
        long cooldownMillis = config.getCooldownHours() * 3600000L;
        long timeSinceLastBuy = currentTimestamp - lastBuyTimestamp;

        if (timeSinceLastBuy < cooldownMillis) {
            // Still in cooldown
            return TradingDecision.Hold.INSTANCE;
        }

        if (currentPrice.compareTo(smaThreshold) >= 0) {
            // Price above threshold - no DCA
            return TradingDecision.Hold.INSTANCE;
        }

        // DCA: add to position
        log.info("DCA signal: cooldown passed ({} hours), price {} < threshold {}",
            timeSinceLastBuy / 3600000, currentPrice, smaThreshold);

        // Use SAME position value as initial entry (not current cash %)
        // This ensures consistent DCA sizing regardless of remaining cash
        BigDecimal dcaValue = initialPositionValue;

        // Validate and create increase order
        TradingDecision decision = validateAndCreateIncreaseOrder(
            position, dcaValue, currentPrice, portfolio.getCashBalance(), currentTimestamp
        );
        if (decision != null) {
            return decision;
        }

        return TradingDecision.Hold.INSTANCE;
    }

    /**
     * Validate and create buy order (open new position)
     */
    private TradingDecision validateAndCreateBuyOrder(BigDecimal positionValue, BigDecimal price,
                                                      BigDecimal totalCash, long timestamp) {
        // 1. Check cash reserve
        BigDecimal availableCash = totalCash.subtract(MINIMUM_CASH_RESERVE_USD);
        if (availableCash.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Insufficient cash (reserve required)");
            return null;
        }

        // 2. Calculate quantity
        BigDecimal orderValue = positionValue.min(availableCash);
        BigDecimal quantity = orderValue.divide(price, 8, RoundingMode.HALF_UP);

        // Round quantity using TradingPair precision
        quantity = TradingPair.BTCUSDT.roundQuantity(quantity);

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Quantity too small after rounding");
            return null;
        }

        // 3. Recalculate order value after rounding
        orderValue = quantity.multiply(price);

        // 4. Check minimum order value
        if (orderValue.compareTo(MINIMUM_ORDER_VALUE_USD) < 0) {
            log.debug("Order value ${} below minimum ${}", orderValue, MINIMUM_ORDER_VALUE_USD);
            return null;
        }

        // 5. Verify sufficient cash
        if (orderValue.compareTo(totalCash) > 0) {
            log.debug("Insufficient cash for order");
            return null;
        }

        log.info("Opening position: quantity={}, price={}, value=${}", quantity, price, orderValue);
        return new TradingDecision.OpenPosition(OrderSide.LONG, quantity, price);
    }

    /**
     * Validate and create increase order (DCA - add to existing position)
     */
    private TradingDecision validateAndCreateIncreaseOrder(Position position, BigDecimal additionalValue,
                                                           BigDecimal price, BigDecimal totalCash, long timestamp) {
        // 1. Check cash reserve
        BigDecimal availableCash = totalCash.subtract(MINIMUM_CASH_RESERVE_USD);
        if (availableCash.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Insufficient cash (reserve required)");
            return null;
        }

        // 2. Calculate additional quantity
        BigDecimal orderValue = additionalValue.min(availableCash);
        BigDecimal quantity = orderValue.divide(price, 8, RoundingMode.HALF_UP);

        // Round quantity
        quantity = TradingPair.BTCUSDT.roundQuantity(quantity);

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Quantity too small after rounding");
            return null;
        }

        // 3. Recalculate order value
        orderValue = quantity.multiply(price);

        // 4. Check minimum order value
        if (orderValue.compareTo(MINIMUM_ORDER_VALUE_USD) < 0) {
            log.debug("Order value ${} below minimum ${}", orderValue, MINIMUM_ORDER_VALUE_USD);
            return null;
        }

        // 5. Verify sufficient cash
        if (orderValue.compareTo(totalCash) > 0) {
            log.debug("Insufficient cash for order");
            return null;
        }

        log.info("Increasing position {}: additional quantity={}, price={}, value=${}",
            position.getId(), quantity, price, orderValue);

        // Update lastBuyTimestamp when we successfully create DCA order
        lastBuyTimestamp = timestamp;

        return new TradingDecision.IncreasePosition(position.getId(), quantity, price);
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
