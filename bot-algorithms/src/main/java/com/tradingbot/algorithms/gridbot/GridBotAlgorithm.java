package com.tradingbot.algorithms.gridbot;

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
import java.util.*;

/**
 * Grid Bot Algorithm - LONG only.
 *
 * Strategy:
 * 1. Create a static grid of buy levels below initial price
 * 2. When price drops to a grid level, buy
 * 3. When price rises by take profit % from entry, sell
 *
 * Grid calculation:
 * - Level 0: initialPrice
 * - Level 1: initialPrice * (1 - gridDistance%)
 * - Level 2: initialPrice * (1 - gridDistance%)^2
 * - ...
 */
@Slf4j
public class GridBotAlgorithm implements TradingAlgorithm<GridBotConfig> {

    private final GridBotConfig config;
    private BigDecimal[] gridLevels;  // Buy prices for each grid level
    private BigDecimal quantityPerLevel;  // How much to buy at each level
    private final Set<Integer> filledLevels;  // Track which levels have been filled
    private final Map<String, Integer> positionToLevel;  // Map position ID to grid level

    public GridBotAlgorithm(GridBotConfig config) {
        this.config = config;
        this.config.validate();
        this.filledLevels = new HashSet<>();
        this.positionToLevel = new HashMap<>();
    }

    @Override
    public String getName() {
        return "GridBot";
    }

    @Override
    public GridBotConfig getConfig() {
        return config;
    }

    @Override
    public void initialize(BigDecimal initialPrice) {
        log.debug("Initializing GridBot with price: {}", initialPrice);

        // Calculate grid levels
        gridLevels = new BigDecimal[config.getGridLevels()];
        BigDecimal distanceMultiplier = BigDecimal.ONE.subtract(
            config.getGridDistancePercent().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
        );

        for (int i = 0; i < config.getGridLevels(); i++) {
            // Level i = initialPrice * (1 - distance%)^i
            gridLevels[i] = initialPrice.multiply(
                distanceMultiplier.pow(i)
            ).setScale(2, RoundingMode.HALF_UP);

            log.debug("Grid level {}: {}", i, gridLevels[i]);
        }

        log.debug("Grid initialized with {} levels from {} to {}",
            config.getGridLevels(), gridLevels[0], gridLevels[config.getGridLevels() - 1]);
    }

    /**
     * Calculate quantity per level based on available capital.
     * This should be called when we're about to open a position.
     */
    private BigDecimal calculateQuantityPerLevel(Portfolio portfolio) {
        // Use configured percentage of portfolio
        BigDecimal availableCapital = portfolio.getCashBalance()
            .multiply(config.getPortfolioAllocationPercent())
            .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        // Divide by number of levels
        BigDecimal capitalPerLevel = availableCapital.divide(
            BigDecimal.valueOf(config.getGridLevels()),
            10,
            RoundingMode.HALF_UP
        );

        return capitalPerLevel;
    }

    @Override
    public TradingDecision onCandle(Candle candle, Portfolio portfolio) {
        if (gridLevels == null) {
            throw new IllegalStateException("GridBot not initialized. Call initialize() first.");
        }

        BigDecimal currentPrice = candle.close();

        // First, check if any open positions should be closed (take profit)
        for (Position position : portfolio.getOpenPositions().values()) {
            if (shouldTakeProfit(position, currentPrice)) {
                log.debug("Taking profit on position {} at level {}",
                    position.getId().substring(0, 8),
                    positionToLevel.get(position.getId()));

                // Mark level as available again
                Integer level = positionToLevel.remove(position.getId());
                if (level != null) {
                    filledLevels.remove(level);
                }

                return new TradingDecision.ClosePosition(position.getId(), currentPrice);
            }
        }

        // Second, check if we should open new positions at any grid level
        for (int level = 0; level < gridLevels.length; level++) {
            if (!filledLevels.contains(level) && shouldBuyAtLevel(level, currentPrice)) {
                // Calculate quantity for this level
                BigDecimal capitalPerLevel = calculateQuantityPerLevel(portfolio);
                BigDecimal buyPrice = gridLevels[level];
                BigDecimal quantity = capitalPerLevel.divide(buyPrice, 8, RoundingMode.HALF_UP);

                // Check if we have enough cash
                BigDecimal cost = quantity.multiply(buyPrice);
                if (portfolio.hasEnoughCash(cost)) {
                    log.debug("Opening position at grid level {} (price: {})", level, buyPrice);

                    // We'll track the position after it's created, so we need to return the decision
                    // and handle the tracking in a callback. For now, we'll use metadata to store level.
                    filledLevels.add(level);

                    return new TradingDecision.OpenPosition(
                        OrderSide.LONG,
                        quantity,
                        buyPrice,
                        "level_" + level
                    );
                } else {
                    log.debug("Insufficient cash to buy at level {}. Required: {}, Available: {}",
                        level, cost, portfolio.getCashBalance());
                }
            }
        }

        return TradingDecision.Hold.INSTANCE;
    }

    /**
     * Check if price has reached a grid level for buying
     */
    private boolean shouldBuyAtLevel(int level, BigDecimal currentPrice) {
        BigDecimal levelPrice = gridLevels[level];

        // Buy if current price is at or below grid level
        // We use a small tolerance to account for floating point precision
        BigDecimal tolerance = levelPrice.multiply(new BigDecimal("0.0001")); // 0.01% tolerance

        return currentPrice.compareTo(levelPrice.add(tolerance)) <= 0;
    }

    /**
     * Check if a position should take profit
     */
    private boolean shouldTakeProfit(Position position, BigDecimal currentPrice) {
        BigDecimal takeProfitPrice = position.getEntryPrice().multiply(
            BigDecimal.ONE.add(
                config.getTakeProfitPercent().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
            )
        );

        return currentPrice.compareTo(takeProfitPrice) >= 0;
    }

    @Override
    public AlgorithmState getState() {
        AlgorithmState state = AlgorithmState.builder()
            .algorithmName(getName())
            .lastUpdateTimestamp(System.currentTimeMillis())
            .build();

        // Save grid levels
        List<String> gridLevelsList = new ArrayList<>();
        if (gridLevels != null) {
            for (BigDecimal level : gridLevels) {
                gridLevelsList.add(level.toString());
            }
        }
        state.putState("gridLevels", gridLevelsList);

        // Save filled levels
        state.putState("filledLevels", new ArrayList<>(filledLevels));

        // Save position to level mapping
        state.putState("positionToLevel", new HashMap<>(positionToLevel));

        return state;
    }

    @Override
    public void restoreState(AlgorithmState state) {
        log.info("Restoring GridBot state");

        // Restore grid levels
        @SuppressWarnings("unchecked")
        List<String> gridLevelsList = (List<String>) state.getStateData().get("gridLevels");
        if (gridLevelsList != null) {
            gridLevels = new BigDecimal[gridLevelsList.size()];
            for (int i = 0; i < gridLevelsList.size(); i++) {
                gridLevels[i] = new BigDecimal(gridLevelsList.get(i));
            }
        }

        // Restore filled levels
        @SuppressWarnings("unchecked")
        List<Integer> filledLevelsList = (List<Integer>) state.getStateData().get("filledLevels");
        if (filledLevelsList != null) {
            filledLevels.clear();
            filledLevels.addAll(filledLevelsList);
        }

        // Restore position to level mapping
        @SuppressWarnings("unchecked")
        Map<String, Integer> positionLevelMap = (Map<String, Integer>) state.getStateData().get("positionToLevel");
        if (positionLevelMap != null) {
            positionToLevel.clear();
            positionToLevel.putAll(positionLevelMap);
        }

        log.info("GridBot state restored with {} levels, {} filled",
            gridLevels != null ? gridLevels.length : 0, filledLevels.size());
    }

    @Override
    public void onPositionOpened(Position position) {
        if (position.getMetadata() != null && position.getMetadata().startsWith("level_")) {
            int level = Integer.parseInt(position.getMetadata().substring(6));
            positionToLevel.put(position.getId(), level);
            log.debug("Tracking position {} at level {}", position.getId().substring(0, 8), level);
        }
    }

    @Override
    public void onPositionClosed(ClosedPosition closedPosition) {
        // Position already removed from positionToLevel map in onCandle() when we decide to close
        // This is just for logging or additional cleanup if needed
        log.debug("Position {} closed", closedPosition.getId().substring(0, 8));
    }
}
