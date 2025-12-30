package com.tradingbot.algorithms.recalculatinggrid;

import com.tradingbot.core.algorithms.AlgorithmState;
import com.tradingbot.core.algorithms.TradingAlgorithm;
import com.tradingbot.core.algorithms.TradingDecision;
import com.tradingbot.core.models.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Recalculating Grid Trading Algorithm.
 *
 * Strategy:
 * 1. Create grid of levels around initial price
 * 2. Buy when price hits grid level
 * 3. Take profit individually per position
 * 4. TOP RESET: When price exceeds top + X%, close all positions and recalculate grid
 * 5. BOTTOM RESET (HARD): When price falls below bottom - Y%, close all positions and recalculate grid
 */
@Slf4j
public class RecalculatingGridAlgorithm implements TradingAlgorithm<RecalculatingGridConfig> {

    private final RecalculatingGridConfig config;
    private final List<GridLevel> gridLevels;
    private final Map<String, TrackedPosition> positionById;
    private final Set<GridLevel> filledLevels;
    private BigDecimal initialCapital;
    private int resetCount = 0;

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
        private final long openTimestamp;
        private final int gridLevelIndex;
    }

    public RecalculatingGridAlgorithm(RecalculatingGridConfig config) {
        this.config = config;
        this.config.validate();
        this.gridLevels = new ArrayList<>();
        this.positionById = new HashMap<>();
        this.filledLevels = new HashSet<>();
    }

    @Override
    public String getName() {
        return "RecalculatingGrid";
    }

    @Override
    public RecalculatingGridConfig getConfig() {
        return config;
    }

    @Override
    public void initialize(BigDecimal initialPrice) {
        initializeGrid(initialPrice);

        log.debug("RecalculatingGrid initialized with {} levels, spacing={}%, tp={}%, gridCapital={}%",
            config.getGridLevels(),
            config.getGridSpacingPercent(),
            config.getTakeProfitPercent(),
            config.getTotalGridCapitalPercent());
        log.debug("Initial price: {}, Bottom level: {}, Top level: {}",
            initialPrice,
            getBottomLevel().getPrice(),
            getTopLevel().getPrice());
    }

    private void initializeGrid(BigDecimal startPrice) {
        gridLevels.clear();
        filledLevels.clear();

        int totalLevels = config.getGridLevels();
        int levelsBelow = totalLevels / 2;
        int levelsAbove = totalLevels - levelsBelow - 1;

        // Create buy levels below start price
        for (int i = 1; i <= levelsBelow; i++) {
            BigDecimal multiplier = pow(
                BigDecimal.ONE.subtract(config.getGridSpacingPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)),
                i
            );
            BigDecimal levelPrice = startPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            GridLevel level = new GridLevel(levelPrice, "buy", -i);
            level.setTakeProfitPrice(calculateTakeProfit(levelPrice));
            gridLevels.add(level);
        }

        // Neutral level at start price
        gridLevels.add(new GridLevel(startPrice.setScale(2, RoundingMode.HALF_UP), "neutral", 0));

        // Create sell levels above start price (for reference)
        for (int i = 1; i <= levelsAbove; i++) {
            BigDecimal multiplier = pow(
                BigDecimal.ONE.add(config.getGridSpacingPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)),
                i
            );
            BigDecimal levelPrice = startPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            gridLevels.add(new GridLevel(levelPrice, "sell", i));
        }

        gridLevels.sort(Comparator.comparing(GridLevel::getPrice));
    }

    private BigDecimal pow(BigDecimal base, int exponent) {
        BigDecimal result = BigDecimal.ONE;
        for (int i = 0; i < exponent; i++) {
            result = result.multiply(base);
        }
        return result.setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTakeProfit(BigDecimal entryPrice) {
        return entryPrice
            .multiply(BigDecimal.ONE.add(config.getTakeProfitPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)))
            .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public TradingDecision onCandle(Candle candle, Portfolio portfolio) {
        BigDecimal currentPrice = candle.close();

        // Initialize capital on first candle
        if (initialCapital == null) {
            initialCapital = portfolio.getCashBalance();
        }

        // Check for top reset trigger
        GridLevel topLevel = getTopLevel();
        BigDecimal topResetPrice = topLevel.getPrice()
            .multiply(BigDecimal.ONE.add(config.getTopResetTriggerPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)))
            .setScale(2, RoundingMode.HALF_UP);

        if (currentPrice.compareTo(topResetPrice) >= 0) {
            log.debug("TOP RESET triggered! Price {} >= top reset level {}", currentPrice, topResetPrice);
            return handleTopReset(currentPrice);
        }

        // Check for bottom reset trigger
        GridLevel bottomLevel = getBottomLevel();
        BigDecimal bottomResetPrice = bottomLevel.getPrice()
            .multiply(BigDecimal.ONE.subtract(config.getBottomResetTriggerPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)))
            .setScale(2, RoundingMode.HALF_UP);

        if (currentPrice.compareTo(bottomResetPrice) <= 0) {
            log.debug("BOTTOM RESET triggered! Price {} <= bottom reset level {}", currentPrice, bottomResetPrice);
            return handleBottomReset(currentPrice);
        }

        // 1. Check TP for all open positions
        for (TrackedPosition tracked : new ArrayList<>(positionById.values())) {
            if (currentPrice.compareTo(tracked.getTakeProfitPrice()) >= 0) {
                log.debug("TP hit for position {} at price {} (target: {})",
                    tracked.getPositionId(), currentPrice, tracked.getTakeProfitPrice());
                return new TradingDecision.ClosePosition(
                    tracked.getPositionId(),
                    tracked.getTakeProfitPrice()
                );
            }
        }

        // 2. Check for buy level fills
        for (GridLevel level : gridLevels) {
            if ("buy".equals(level.getType()) && !filledLevels.contains(level)) {
                if (currentPrice.compareTo(level.getPrice()) <= 0) {
                    log.debug("Buy level hit at price {} (level: {})", currentPrice, level.getPrice());

                    // Calculate position size: (initialCapital * totalGridCapitalPercent / 100) / gridLevels
                    BigDecimal positionValue = initialCapital
                        .multiply(config.getTotalGridCapitalPercent())
                        .divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(config.getGridLevels()), RoundingMode.HALF_UP);

                    if (positionValue.compareTo(portfolio.getCashBalance()) > 0) {
                        log.debug("Insufficient balance for position");
                        continue;
                    }

                    BigDecimal quantity = positionValue.divide(level.getPrice(), 8, RoundingMode.HALF_UP);

                    filledLevels.add(level);

                    return new TradingDecision.OpenPosition(
                        OrderSide.LONG,
                        quantity,
                        level.getPrice()
                    );
                }
            }
        }

        return TradingDecision.Hold.INSTANCE;
    }

    private TradingDecision handleTopReset(BigDecimal currentPrice) {
        log.debug("Executing TOP RESET - closing all {} positions and recalculating grid", positionById.size());
        resetCount++;

        if (!positionById.isEmpty()) {
            // Close oldest position first, engine will call this again for next position
            String oldestPositionId = positionById.keySet().iterator().next();
            return new TradingDecision.ClosePosition(oldestPositionId, currentPrice);
        } else {
            // All positions closed, recalculate grid
            log.debug("All positions closed, recalculating grid from price {}", currentPrice);
            initializeGrid(currentPrice);
            return TradingDecision.Hold.INSTANCE;
        }
    }

    private TradingDecision handleBottomReset(BigDecimal currentPrice) {
        log.debug("Executing BOTTOM RESET (HARD) - closing all {} positions with loss and recalculating grid", positionById.size());
        resetCount++;

        if (!positionById.isEmpty()) {
            // Close oldest position first, engine will call this again for next position
            String oldestPositionId = positionById.keySet().iterator().next();
            return new TradingDecision.ClosePosition(oldestPositionId, currentPrice);
        } else {
            // All positions closed, recalculate grid
            log.debug("All positions closed after hard reset, recalculating grid from price {}", currentPrice);
            initializeGrid(currentPrice);
            return TradingDecision.Hold.INSTANCE;
        }
    }

    private GridLevel getBottomLevel() {
        return gridLevels.stream()
            .min(Comparator.comparing(GridLevel::getPrice))
            .orElseThrow(() -> new IllegalStateException("Grid has no levels"));
    }

    private GridLevel getTopLevel() {
        return gridLevels.stream()
            .max(Comparator.comparing(GridLevel::getPrice))
            .orElseThrow(() -> new IllegalStateException("Grid has no levels"));
    }

    @Override
    public AlgorithmState getState() {
        Map<String, Object> stateData = new HashMap<>();

        // Save grid levels
        List<Map<String, Object>> gridLevelsList = new ArrayList<>();
        for (GridLevel level : gridLevels) {
            Map<String, Object> levelMap = new HashMap<>();
            levelMap.put("price", level.getPrice().toPlainString());
            levelMap.put("type", level.getType());
            levelMap.put("levelIndex", level.getLevelIndex());
            if (level.getTakeProfitPrice() != null) {
                levelMap.put("takeProfitPrice", level.getTakeProfitPrice().toPlainString());
            }
            gridLevelsList.add(levelMap);
        }
        stateData.put("gridLevels", gridLevelsList);

        // Save position tracking map
        List<Map<String, Object>> positionsList = new ArrayList<>();
        for (TrackedPosition tracked : positionById.values()) {
            Map<String, Object> posMap = new HashMap<>();
            posMap.put("positionId", tracked.getPositionId());
            posMap.put("entryPrice", tracked.getEntryPrice().toPlainString());
            posMap.put("quantity", tracked.getQuantity().toPlainString());
            posMap.put("takeProfitPrice", tracked.getTakeProfitPrice().toPlainString());
            posMap.put("openTimestamp", tracked.getOpenTimestamp());
            posMap.put("gridLevelIndex", tracked.getGridLevelIndex());
            positionsList.add(posMap);
        }
        stateData.put("positionsById", positionsList);

        // Save filled levels indices
        List<Integer> filledIndices = filledLevels.stream()
            .map(GridLevel::getLevelIndex)
            .toList();
        stateData.put("filledLevelIndices", filledIndices);

        // Save initial capital
        if (initialCapital != null) {
            stateData.put("initialCapital", initialCapital.toPlainString());
        }

        // Save reset count
        stateData.put("resetCount", resetCount);

        AlgorithmState state = AlgorithmState.builder()
            .algorithmName(getName())
            .lastUpdateTimestamp(System.currentTimeMillis())
            .stateData(stateData)
            .build();

        return state;
    }

    @Override
    public void restoreState(AlgorithmState state) {
        log.debug("Restoring RecalculatingGrid state");

        // Restore grid levels
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gridLevelsList = (List<Map<String, Object>>) state.getStateData().get("gridLevels");
        if (gridLevelsList != null) {
            gridLevels.clear();
            for (Map<String, Object> levelMap : gridLevelsList) {
                BigDecimal price = new BigDecimal((String) levelMap.get("price"));
                String type = (String) levelMap.get("type");
                int levelIndex = (Integer) levelMap.get("levelIndex");
                GridLevel level = new GridLevel(price, type, levelIndex);

                if (levelMap.containsKey("takeProfitPrice")) {
                    level.setTakeProfitPrice(new BigDecimal((String) levelMap.get("takeProfitPrice")));
                }
                gridLevels.add(level);
            }
        }

        // Restore positions map
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> positionsList = (List<Map<String, Object>>) state.getStateData().get("positionsById");
        if (positionsList != null) {
            positionById.clear();
            for (Map<String, Object> posMap : positionsList) {
                TrackedPosition tracked = new TrackedPosition(
                    (String) posMap.get("positionId"),
                    new BigDecimal((String) posMap.get("entryPrice")),
                    new BigDecimal((String) posMap.get("quantity")),
                    new BigDecimal((String) posMap.get("takeProfitPrice")),
                    ((Number) posMap.get("openTimestamp")).longValue(),
                    (Integer) posMap.get("gridLevelIndex")
                );
                positionById.put(tracked.getPositionId(), tracked);
            }
        }

        // Restore filled levels
        @SuppressWarnings("unchecked")
        List<Integer> filledIndices = (List<Integer>) state.getStateData().get("filledLevelIndices");
        if (filledIndices != null) {
            filledLevels.clear();
            for (Integer index : filledIndices) {
                gridLevels.stream()
                    .filter(level -> level.getLevelIndex() == index)
                    .findFirst()
                    .ifPresent(filledLevels::add);
            }
        }

        // Restore initial capital
        String initialCapitalStr = (String) state.getStateData().get("initialCapital");
        if (initialCapitalStr != null) {
            initialCapital = new BigDecimal(initialCapitalStr);
        }

        // Restore reset count
        Object resetCountObj = state.getStateData().get("resetCount");
        if (resetCountObj != null) {
            resetCount = (Integer) resetCountObj;
        }

        log.debug("RecalculatingGrid state restored with {} levels, {} open positions, {} resets",
            gridLevels.size(), positionById.size(), resetCount);
    }

    @Override
    public void onPositionOpened(Position position) {
        TrackedPosition tracked = new TrackedPosition(
            position.getId(),
            position.getEntryPrice(),
            position.getQuantity(),
            calculateTakeProfit(position.getEntryPrice()),
            position.getOpenTimestamp(),
            0
        );
        positionById.put(position.getId(), tracked);
        log.debug("Position opened: {} @ {} (TP: {})", position.getId(), position.getEntryPrice(), tracked.getTakeProfitPrice());
    }

    @Override
    public void onPositionClosed(ClosedPosition closedPosition) {
        TrackedPosition tracked = positionById.remove(closedPosition.getId());
        if (tracked != null) {
            // Remove from filled levels if it was a grid level position
            gridLevels.stream()
                .filter(level -> "buy".equals(level.getType()))
                .filter(level -> level.getPrice().compareTo(tracked.getEntryPrice()) == 0)
                .findFirst()
                .ifPresent(filledLevels::remove);

            log.debug("Position closed: {} with P&L: {}", closedPosition.getId(), closedPosition.getRealizedPnL());
        }
    }
}
