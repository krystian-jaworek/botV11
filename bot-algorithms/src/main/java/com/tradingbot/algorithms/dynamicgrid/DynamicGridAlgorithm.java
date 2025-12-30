package com.tradingbot.algorithms.dynamicgrid;

import com.tradingbot.core.algorithms.TradingAlgorithm;
import com.tradingbot.core.algorithms.TradingDecision;
import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.ClosedPosition;
import com.tradingbot.core.models.OrderSide;
import com.tradingbot.core.models.Portfolio;
import com.tradingbot.core.models.Position;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Dynamic Grid Trading Algorithm - LONG only.
 *
 * Features:
 * - Grid dynamically shifts down when price breaches bottom
 * - Grid expands upward when price exceeds top trigger
 * - FIFO queue for closing oldest positions first
 * - Individual TP for each position
 */
@Slf4j
public class DynamicGridAlgorithm implements TradingAlgorithm<DynamicGridConfig> {

    private final DynamicGridConfig config;
    private final List<GridLevel> gridLevels;
    private final Deque<TrackedPosition> openPositionsQueue;  // FIFO queue
    private final Map<String, TrackedPosition> positionById;
    private final Set<GridLevel> filledLevels;
    private BigDecimal initialCapital;
    private BigDecimal fixedPositionSize;

    @Data
    private static class GridLevel {
        private final BigDecimal price;
        private final String type;  // "buy", "sell", "neutral"
        private final int levelIndex;
        private BigDecimal takeProfitPrice;  // For buy levels

        public GridLevel(BigDecimal price, String type, int levelIndex) {
            this.price = price;
            this.type = type;
            this.levelIndex = levelIndex;
        }
    }

    @Data
    private static class TrackedPosition {
        private final String positionId;
        private final BigDecimal entryPrice;
        private final BigDecimal quantity;
        private final BigDecimal takeProfitPrice;
        private final long entryTimestamp;
        private final int gridLevelIndex;
    }

    public DynamicGridAlgorithm(DynamicGridConfig config) {
        this.config = config;
        this.config.validate();
        this.gridLevels = new ArrayList<>();
        this.openPositionsQueue = new LinkedList<>();
        this.positionById = new HashMap<>();
        this.filledLevels = new HashSet<>();
    }

    @Override
    public String getName() {
        return "DynamicGrid";
    }

    @Override
    public DynamicGridConfig getConfig() {
        return config;
    }

    @Override
    public void initialize(Portfolio portfolio, Candle firstCandle) {
        this.initialCapital = portfolio.getCashBalance();

        if (config.isUseFixedPositionSize()) {
            this.fixedPositionSize = initialCapital
                .multiply(config.getPositionSizePercent())
                .divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
        }

        initializeGrid(firstCandle.close());

        log.info("DynamicGrid initialized with {} levels, spacing={}%, tp={}%, size={}%",
            config.getGridLevels(),
            config.getGridSpacingPercent(),
            config.getTakeProfitPercent(),
            config.getPositionSizePercent());
        log.info("Initial price: {}, Bottom level: {}, Top level: {}",
            firstCandle.close(),
            getBottomLevel().getPrice(),
            getTopLevel().getPrice());
    }

    private void initializeGrid(BigDecimal startPrice) {
        gridLevels.clear();

        // Calculate levels below and above start price
        int levelsBelow = config.getGridLevels() / 2;
        int levelsAbove = config.getGridLevels() - levelsBelow - 1;

        // Levels below (buy orders)
        for (int i = 1; i <= levelsBelow; i++) {
            BigDecimal multiplier = BigDecimal.ONE
                .subtract(config.getGridSpacingPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
            BigDecimal price = startPrice.multiply(pow(multiplier, i));

            GridLevel level = new GridLevel(price, "buy", -i);
            level.setTakeProfitPrice(calculateTakeProfit(price));
            gridLevels.add(level);
        }

        // Start price level
        GridLevel startLevel = new GridLevel(startPrice, "neutral", 0);
        gridLevels.add(startLevel);

        // Levels above (sell targets)
        for (int i = 1; i <= levelsAbove; i++) {
            BigDecimal multiplier = BigDecimal.ONE
                .add(config.getGridSpacingPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
            BigDecimal price = startPrice.multiply(pow(multiplier, i));

            GridLevel level = new GridLevel(price, "sell", i);
            gridLevels.add(level);
        }

        // Sort by price
        gridLevels.sort(Comparator.comparing(GridLevel::getPrice));
    }

    private BigDecimal pow(BigDecimal base, int exponent) {
        BigDecimal result = BigDecimal.ONE;
        for (int i = 0; i < exponent; i++) {
            result = result.multiply(base);
        }
        return result;
    }

    private BigDecimal calculateTakeProfit(BigDecimal entryPrice) {
        return entryPrice.multiply(
            BigDecimal.ONE.add(config.getTakeProfitPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
        );
    }

    private GridLevel getBottomLevel() {
        return gridLevels.stream()
            .filter(l -> "buy".equals(l.getType()))
            .min(Comparator.comparing(GridLevel::getPrice))
            .orElse(gridLevels.get(0));
    }

    private GridLevel getTopLevel() {
        return gridLevels.stream()
            .max(Comparator.comparing(GridLevel::getPrice))
            .orElse(gridLevels.get(gridLevels.size() - 1));
    }

    @Override
    public TradingDecision onCandle(Candle candle, Portfolio portfolio) {
        BigDecimal currentPrice = candle.close();

        // 1. Check TP for all open positions
        for (TrackedPosition tracked : new ArrayList<>(openPositionsQueue)) {
            if (currentPrice.compareTo(tracked.getTakeProfitPrice()) >= 0) {
                log.debug("TP hit for position {} at price {} (target: {})",
                    tracked.getPositionId(), currentPrice, tracked.getTakeProfitPrice());
                return TradingDecision.closePosition(
                    tracked.getPositionId(),
                    tracked.getTakeProfitPrice(),
                    "Take Profit"
                );
            }
        }

        // 2. Check for buy level fills
        for (GridLevel level : gridLevels) {
            if ("buy".equals(level.getType()) && !filledLevels.contains(level)) {
                if (currentPrice.compareTo(level.getPrice()) <= 0) {
                    log.debug("Buy level hit at price {} (level: {})", currentPrice, level.getPrice());

                    // Calculate position size
                    BigDecimal positionValue;
                    if (config.isUseFixedPositionSize()) {
                        positionValue = fixedPositionSize;
                    } else {
                        positionValue = portfolio.getCashBalance()
                            .multiply(config.getPositionSizePercent())
                            .divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
                    }

                    if (positionValue.compareTo(portfolio.getCashBalance()) > 0) {
                        log.debug("Insufficient balance for position");
                        continue;
                    }

                    BigDecimal quantity = positionValue.divide(level.getPrice(), 8, RoundingMode.HALF_UP);

                    filledLevels.add(level);
                    return TradingDecision.openPosition(
                        OrderSide.BUY,
                        quantity,
                        level.getPrice(),
                        "Grid Level " + level.getLevelIndex()
                    );
                }
            }
        }

        // 3. Check bottom breach
        GridLevel bottomLevel = getBottomLevel();
        while (currentPrice.compareTo(bottomLevel.getPrice()) < 0) {
            log.info("Bottom breach! Price {} < bottom level {}", currentPrice, bottomLevel.getPrice());

            // Close oldest position if exists
            if (!openPositionsQueue.isEmpty()) {
                TrackedPosition oldest = openPositionsQueue.peekFirst();
                log.info("Closing oldest position {} (entry: {}) at price {}",
                    oldest.getPositionId(), oldest.getEntryPrice(), currentPrice);

                return TradingDecision.closePosition(
                    oldest.getPositionId(),
                    currentPrice,
                    "Bottom Breach"
                );
            }

            // Add new bottom level
            BigDecimal newBottomPrice = bottomLevel.getPrice().multiply(
                BigDecimal.ONE.subtract(config.getGridSpacingPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            );

            GridLevel newLevel = new GridLevel(newBottomPrice, "buy", bottomLevel.getLevelIndex() - 1);
            newLevel.setTakeProfitPrice(calculateTakeProfit(newBottomPrice));
            gridLevels.add(newLevel);
            gridLevels.sort(Comparator.comparing(GridLevel::getPrice));

            // Remove old bottom level
            gridLevels.remove(bottomLevel);
            filledLevels.remove(bottomLevel);

            log.info("Added new bottom level at {}, removed old at {}", newBottomPrice, bottomLevel.getPrice());

            bottomLevel = getBottomLevel();
        }

        // 4. Check top expansion
        GridLevel topLevel = getTopLevel();
        BigDecimal topTriggerPrice = topLevel.getPrice().multiply(
            BigDecimal.ONE.add(config.getTopTriggerPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
        );

        if (currentPrice.compareTo(topTriggerPrice) > 0) {
            log.info("Top expansion! Price {} > trigger {}", currentPrice, topTriggerPrice);

            // Close oldest position if exists
            if (!openPositionsQueue.isEmpty()) {
                TrackedPosition oldest = openPositionsQueue.peekFirst();
                log.info("Closing oldest position {} (entry: {}) at price {} for top expansion",
                    oldest.getPositionId(), oldest.getEntryPrice(), currentPrice);

                return TradingDecision.closePosition(
                    oldest.getPositionId(),
                    currentPrice,
                    "Top Expansion"
                );
            }

            // Add new top level
            BigDecimal newTopPrice = topLevel.getPrice().multiply(
                BigDecimal.ONE.add(config.getGridSpacingPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            );

            GridLevel newLevel = new GridLevel(newTopPrice, "sell", topLevel.getLevelIndex() + 1);
            gridLevels.add(newLevel);
            gridLevels.sort(Comparator.comparing(GridLevel::getPrice));

            log.info("Added new top level at {}", newTopPrice);
        }

        return TradingDecision.hold();
    }

    @Override
    public void onPositionOpened(Position position) {
        TrackedPosition tracked = new TrackedPosition(
            position.getId(),
            position.getEntryPrice(),
            position.getQuantity(),
            calculateTakeProfit(position.getEntryPrice()),
            position.getEntryTimestamp(),
            0  // Level index would need to be stored
        );

        openPositionsQueue.addLast(tracked);
        positionById.put(position.getId(), tracked);

        log.debug("Position opened: {} at {} (TP: {}), queue size: {}",
            position.getId(), position.getEntryPrice(), tracked.getTakeProfitPrice(), openPositionsQueue.size());
    }

    @Override
    public void onPositionClosed(ClosedPosition closedPosition) {
        TrackedPosition tracked = positionById.remove(closedPosition.getPositionId());
        if (tracked != null) {
            openPositionsQueue.remove(tracked);
            log.debug("Position closed: {} at {} (entry: {}), PnL: {}, queue size: {}",
                closedPosition.getPositionId(),
                closedPosition.getExitPrice(),
                closedPosition.getEntryPrice(),
                closedPosition.getProfitLoss(),
                openPositionsQueue.size());
        }
    }
}
