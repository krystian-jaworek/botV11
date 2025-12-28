# TrendFollowing Algorithm - Implementation Decisions

This document contains all architectural and implementation decisions made during the development of the TrendFollowing trading algorithm with multiple timeframes.

## User Decisions (Q1-Q8)

### Q1: Timeframe Aggregation Strategy
**Decision**: Option C - Finest timeframe with dual aggregation

**Implementation**:
- Candle files contain the finest timeframe (e.g., 1h candles)
- System aggregates to BOTH primary (1d) and secondary (1w) timeframes
- Pre-calculation during `preCalculateIndicators()`:
  ```java
  // Aggregate primary candles to secondary timeframe
  List<Candle> secondaryCandles = TimeframeAggregator.aggregate(
      candles, primaryTimeframe, secondaryTimeframe
  );
  ```

**Rationale**: Maximum flexibility - single data source supports multiple timeframe analyses without data duplication.

---

### Q2: Swing Low Detection Method
**Decision**: Option B - Swing low with pivot-based detection

**Implementation**:
- `SwingDetector.findSwingLows(candles, pivotPeriods)`
- A candle is a swing low if its low < all surrounding candles within `pivotPeriods`
- Configurable via `swingDetectionPeriods` parameter (default: 5-7)
- Validation uses `higherLowsPeriods` to check for N consecutive higher lows
- Lookback window controlled by `higherLowsLookback` (default: 20 candles)

**Example**:
```java
// Detect swing lows with 5-period pivot
List<SwingPoint> swingLows = SwingDetector.findSwingLows(candles, 5);

// Validate pattern: at least 3 higher lows in last 20 candles
boolean hasPattern = PatternValidator.hasHigherLows(
    recentSwingLows,
    config.getHigherLowsPeriods()  // 3
);
```

**Rationale**: More robust than simple local minima - filters noise and identifies significant price structure.

---

### Q3: MACD Histogram Growth Validation
**Decision**: Option A - Strictly growing (ascending) histogram

**Implementation**:
- `PatternValidator.isHistogramGrowing(macd, minPeriods, atIndex)`
- Validates: `histogram[i] > histogram[i-1] > histogram[i-2] > ...`
- Must be true for BOTH primary AND secondary timeframes
- Configurable via `minHistogramGrowth` parameter (default: 2-3 periods)

**Example**:
```java
// Check if histogram is strictly growing for 3 periods on BOTH timeframes
boolean primaryGrowing = PatternValidator.isHistogramGrowing(
    state.getMacdPrimary(), config.getMinHistogramGrowth(), currentIndex
);
boolean secondaryGrowing = PatternValidator.isHistogramGrowing(
    state.getMacdSecondary(), config.getMinHistogramGrowth(), secondaryIndex
);

if (primaryGrowing && secondaryGrowing) {
    // Entry condition met
}
```

**Rationale**: Strong momentum confirmation - ensures accelerating bullish pressure on both timeframes.

---

### Q4: Position Management Strategy
**Decision**: One position at a time with partial closing on TP levels

**Implementation**:
- Maximum 1 open position per algorithm instance
- Position opened with full quantity calculated from `positionSizePct` (10%)
- Partial closes executed at each TP level (equal portions)
- Tracking via `TrendFollowingState`:
  ```java
  String currentPositionId;
  BigDecimal initialQuantity;
  BigDecimal remainingQuantity;
  Set<Integer> tpLevelsHit;  // {0, 1} means TP1 and TP2 already hit
  ```

**Example - 3 TP levels**:
- Entry: Open 1.0 BTC at $50,000
- TP1 (200%): Close 0.33 BTC, remaining 0.67 BTC
- TP2 (300%): Close 0.33 BTC, remaining 0.34 BTC
- TP3 (500%): Close 0.34 BTC, position fully closed

**Rationale**: Balances profit-taking with trend-following - locks in gains while allowing winners to run.

---

### Q5: Exit Priority Order
**Decision**: Stop Loss > Death Cross > Take Profit > Trailing Stop

**Implementation** (from `checkExitConditions()`):
```java
// 1. STOP LOSS (highest priority - capital protection)
if (currentPrice <= stopLossPrice) {
    return TradingDecision.closePosition(positionId, currentPrice, "Stop Loss");
}

// 2. TRAILING STOP (second priority - lock in profits)
if (trailingStopActive && currentPrice <= trailingStopPrice) {
    return TradingDecision.closePosition(positionId, currentPrice, "Trailing Stop");
}

// 3. DEATH CROSS EXIT (third priority - trend reversal)
if (enableDeathCrossExit && isDeathCross(...)) {
    return TradingDecision.closePosition(positionId, currentPrice, "Death Cross");
}

// 4. TAKE PROFIT LEVELS (fourth priority - partial exits)
for (int i = 0; i < tpPrices.size(); i++) {
    if (!tpLevelsHit.contains(i) && currentPrice >= tpPrices.get(i)) {
        return TradingDecision.closePartialPosition(..., "TP" + (i+1));
    }
}

// 5. UPDATE TRAILING STOP (lowest priority - maintenance)
updateTrailingStopLogic(...);
```

**Rationale**:
- SL first: Protect against catastrophic loss
- Trailing second: Secure accumulated profits
- Death Cross third: Exit on trend reversal signal
- TP last: Opportunistic profit-taking

---

### Q6: Position Sizing Strategy
**Decision**: 10% of available equity per new position

**Implementation**:
```java
// TrendFollowingConfig
BigDecimal positionSizePct = new BigDecimal("10");  // 10%
boolean useAvailableEquity = true;

// In checkEntryConditions()
BigDecimal availableEquity = portfolio.getTotalEquity();
BigDecimal positionValue = availableEquity
    .multiply(config.getPositionSizePct())
    .divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
BigDecimal quantity = positionValue.divide(currentPrice, 8, RoundingMode.HALF_UP);
```

**Example**:
- Total equity: $10,000
- Position size: 10% = $1,000
- BTC price: $50,000
- Quantity: $1,000 / $50,000 = 0.02 BTC

**Rationale**: Conservative risk management - limits single position exposure while allowing portfolio growth.

---

### Q7: Permutation Parameter Ranges
**Decision**: Reduced ranges to ~1000 valid combinations

**Full Specification**:
```java
// EMA PRIMARY (2 × 3 = 6 combinations after filtering)
emaFastPrimaryRange:  [50, 100]
emaSlowPrimaryRange:  [150, 200, 250]
// Filter: fast < slow

// EMA SECONDARY (2 × 2 = 4 combinations after filtering)
emaFastSecondaryRange:  [20, 30]
emaSlowSecondaryRange:  [50, 100]
// Filter: fast < slow

// MACD (2 × 2 × 2 = 8 combinations after filtering)
macdFastRange:   [12, 16]
macdSlowRange:   [26, 32]
macdSignalRange: [9, 12]
// Filter: fast < slow

// HIGHER LOWS DETECTION (3 × 2 = 6 combinations)
higherLowsPeriodsRange:      [3, 4, 5]      // N consecutive higher lows required
swingDetectionPeriodsRange:  [5, 7]         // Pivot periods for swing detection

// HISTOGRAM GROWTH (2 values)
minHistogramGrowthRange: [2, 3]  // Minimum consecutive growing periods

// TAKE PROFIT LEVELS (2 options)
targetProfitPctOptions: [
    [200%, 300%, 500%],  // Conservative
    [250%, 400%, 700%]   // Aggressive
]

// STOP LOSS (3 values)
stopLossPctRange: [15%, 20%, 25%]

// TRAILING STOP (2 × 2 = 4 combinations)
trailingStopActivationPctRange: [50%, 75%]   // Activation threshold
trailingStopDistancePctRange:   [20%, 25%]   // Distance from peak

// FIXED PARAMETERS
higherLowsLookback: 20           // Lookback window for higher lows
enableDeathCrossExit: true       // Always enabled
positionSizePct: 10%             // Fixed position sizing
useAvailableEquity: true         // Always use available equity
```

**Total Combinations Calculation**:
```
Valid EMA Primary: 6 (after fast < slow filter)
Valid EMA Secondary: 4 (after fast < slow filter)
Valid MACD: 8 (after fast < slow filter)
Higher Lows: 3 × 2 = 6
Histogram: 2
TP Options: 2
Stop Loss: 3
Trailing: 2 × 2 = 4

Total ≈ 6 × 4 × 8 × 6 × 2 × 2 × 3 × 4 = ~1,152 configurations
```

**Implementation**:
- `TrendFollowingParameterPermutation.defaultPermutation()`
- Nested loops with validation filters
- Pre-calculation of indicators for each configuration
- Parallel execution with Java 21 Virtual Threads

**Rationale**: Balances exploration space with computational feasibility - covers key parameter variations while remaining practical for backtesting.

---

### Q8: Partial Closing with Tracking
**Decision**: Equal portions closed at each TP with state tracking

**Implementation**:

**Core Models Extended**:
```java
// TradingDecision.java - New sealed record
record ClosePositionPartial(
    String positionId,
    BigDecimal quantity,      // Quantity to close (not full)
    BigDecimal price,
    String reason             // "TP1", "TP2", "TP3"
) implements TradingDecision {}

// ClosedPosition.java - Partial close support
@Builder.Default boolean isPartialClose = false;
BigDecimal remainingQuantity;

// Portfolio.java - New method
public ClosedPosition closePartialPosition(
    String positionId,
    BigDecimal quantityToClose,
    BigDecimal exitPrice,
    long closeTimestamp
) {
    Position position = openPositions.get(positionId);
    BigDecimal remaining = position.getQuantity().subtract(quantityToClose);

    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
        // Fully closed - remove from openPositions
    } else {
        // Partially closed - update position quantity
        Position updated = position.withQuantity(remaining);
        openPositions.put(positionId, updated);
    }

    return ClosedPosition.fromPartialClose(..., remaining);
}
```

**BacktestEngine Integration**:
```java
// BacktestEngine.java - Switch case added
case TradingDecision.ClosePositionPartial partial -> {
    Position position = portfolio.getOpenPositions().get(partial.positionId());
    ClosedPosition closedPosition = executor.closePartialPosition(
        position, partial.quantity(), partial.price(), currentCandle.timestamp()
    );
    algorithm.onPositionClosed(closedPosition);
}
```

**TrendFollowing State Tracking**:
```java
// TrendFollowingState.java
Set<Integer> tpLevelsHit = new HashSet<>();  // Track which TPs were hit

// In checkExitConditions() - Calculate portions
BigDecimal remainingQuantity = state.getRemainingQuantity();
int remainingTpLevels = tpPrices.size() - tpLevelsHit.size();
BigDecimal portionToClose = remainingQuantity.divide(
    BigDecimal.valueOf(remainingTpLevels),
    8,
    RoundingMode.HALF_UP
);

// Mark TP as hit
tpLevelsHit.add(tpIndex);
```

**Example Execution Flow**:
```
Entry: 1.0 BTC @ $50,000
  → state.initialQuantity = 1.0
  → state.remainingQuantity = 1.0
  → state.tpLevelsHit = {}

TP1 Hit ($150,000):
  → 3 TPs remaining → close 1.0 / 3 = 0.333 BTC
  → state.remainingQuantity = 0.667
  → state.tpLevelsHit = {0}
  → ClosedPosition(isPartialClose=true, remainingQuantity=0.667)

TP2 Hit ($200,000):
  → 2 TPs remaining → close 0.667 / 2 = 0.333 BTC
  → state.remainingQuantity = 0.334
  → state.tpLevelsHit = {0, 1}
  → ClosedPosition(isPartialClose=true, remainingQuantity=0.334)

TP3 Hit ($300,000):
  → 1 TP remaining → close 0.334 / 1 = 0.334 BTC
  → state.remainingQuantity = 0.0
  → state.tpLevelsHit = {0, 1, 2}
  → state.currentPositionId = null (position fully closed)
  → ClosedPosition(isPartialClose=false, remainingQuantity=0.0)
```

**Rationale**:
- Equal portions ensure balanced profit-taking
- State tracking prevents duplicate TP execution
- Clean architecture with minimal changes to core models

---

## Key Features Implemented

### 1. Hybrydowy Trailing Stop (PRO Feature)

**Description**: Advanced trailing stop that dynamically raises TP levels when trailing exceeds them.

**Implementation**:
```java
// TrendFollowingState.java
List<BigDecimal> dynamicTpPrices;  // Initially = static TP prices

// TrendFollowingAlgorithm.java
private void adjustTpLevelsWithTrailing(BigDecimal trailingPrice) {
    List<BigDecimal> updatedTpPrices = new ArrayList<>(state.getDynamicTpPrices());
    boolean anyAdjusted = false;

    for (int i = 0; i < updatedTpPrices.size(); i++) {
        if (state.getTpLevelsHit().contains(i)) continue;  // Skip already hit TPs

        BigDecimal currentTp = updatedTpPrices.get(i);

        if (trailingPrice.compareTo(currentTp) > 0) {
            // Trailing exceeds TP → move TP higher
            BigDecimal newTpPrice = trailingPrice.multiply(new BigDecimal("1.10"));
            updatedTpPrices.set(i, newTpPrice);
            anyAdjusted = true;

            log.info("📈 DYNAMIC TP ADJUSTMENT - TP{} moved from {} to {} (trailing at {})",
                i + 1, currentTp, newTpPrice, trailingPrice);
        }
    }

    if (anyAdjusted) {
        state.setDynamicTpPrices(updatedTpPrices);
    }
}
```

**Example Scenario**:
```
Entry: $50,000
Static TPs: [$150,000 (TP1), $200,000 (TP2), $300,000 (TP3)]
Trailing: Activates at 50% profit ($75,000), distance 20%

Price reaches $180,000:
  → Profit = 260% > 50% activation → Trailing ACTIVE
  → Trailing price = $180,000 - 20% = $144,000
  → $144,000 < $150,000 (TP1) → No adjustment

Price reaches $200,000:
  → Trailing price = $200,000 - 20% = $160,000
  → $160,000 > $150,000 (TP1) → ADJUST TP1
  → New TP1 = $160,000 × 1.10 = $176,000
  → Dynamic TPs: [$176,000, $200,000, $300,000]

Price reaches $250,000:
  → Trailing price = $250,000 - 20% = $200,000
  → $200,000 > $176,000 (TP1) → ADJUST TP1 to $220,000
  → $200,000 = $200,000 (TP2) → ADJUST TP2 to $220,000
  → Dynamic TPs: [$220,000, $220,000, $300,000]
```

**Benefits**:
- Locks in higher profits as trend continues
- Prevents giving back gains when price retraces
- Maintains upside potential while securing floor
- Automatic adaptation to strong trends

---

### 2. Pre-Calculated Indicators

**Challenge**: BacktestEngine processes candles sequentially, but secondary timeframe analysis requires future candles.

**Solution**: Pre-calculate ALL indicators before backtest begins.

**Implementation**:
```java
// TrendFollowingAlgorithm.java
@Override
public void preCalculateIndicators(List<Candle> allCandles) {
    log.info("Pre-calculating indicators for {} candles", allCandles.size());

    // 1. Aggregate to secondary timeframe
    List<Candle> secondaryCandles = TimeframeAggregator.aggregate(
        allCandles,
        config.getPrimaryTimeframe(),
        config.getSecondaryTimeframe()
    );

    // 2. Calculate EMAs for PRIMARY timeframe
    List<BigDecimal> emaFastPrimary = ExponentialMovingAverage.calculate(
        allCandles, config.getEmaFastPrimary()
    );
    List<BigDecimal> emaSlowPrimary = ExponentialMovingAverage.calculate(
        allCandles, config.getEmaSlowPrimary()
    );

    // 3. Calculate EMAs for SECONDARY timeframe
    List<BigDecimal> emaFastSecondary = ExponentialMovingAverage.calculate(
        secondaryCandles, config.getEmaFastSecondary()
    );
    List<BigDecimal> emaSlowSecondary = ExponentialMovingAverage.calculate(
        secondaryCandles, config.getEmaSlowSecondary()
    );

    // 4. Calculate MACD for both timeframes
    List<MACDResult> macdPrimary = MACD.calculate(
        allCandles,
        config.getMacdFast(),
        config.getMacdSlow(),
        config.getMacdSignal()
    );
    List<MACDResult> macdSecondary = MACD.calculate(
        secondaryCandles,
        config.getMacdFast(),
        config.getMacdSlow(),
        config.getMacdSignal()
    );

    // 5. Detect swing lows on primary timeframe
    List<SwingPoint> swingLowsPrimary = SwingDetector.findSwingLows(
        allCandles, config.getSwingDetectionPeriods()
    );

    // 6. Store in state for O(1) lookup during backtest
    state = TrendFollowingState.builder()
        .emaFastPrimary(emaFastPrimary)
        .emaSlowPrimary(emaSlowPrimary)
        .emaFastSecondary(emaFastSecondary)
        .emaSlowSecondary(emaSlowSecondary)
        .macdPrimary(macdPrimary)
        .macdSecondary(macdSecondary)
        .swingLowsPrimary(swingLowsPrimary)
        .build();

    log.info("Indicators pre-calculated successfully");
}
```

**Benefits**:
- O(1) indicator lookup during backtest (vs O(n) recalculation)
- Supports multiple timeframes without complexity
- Clean separation: calculation vs usage
- Enables efficient parallel permutation mode

---

### 3. Parallel Permutation Mode with Virtual Threads

**Implementation**:
```java
// TrendFollowingPermutationRunner.java
public static void main(String[] args) {
    // Generate ~1000 configurations
    List<TrendFollowingConfig> configurations = permutation.generateConfigurations();

    // Create tasks with PRE-CALCULATED indicators
    List<SimulationTask> tasks = new ArrayList<>();
    for (TrendFollowingConfig config : configurations) {
        TrendFollowingAlgorithm algorithm = new TrendFollowingAlgorithm(config);
        algorithm.preCalculateIndicators(candles);  // CRITICAL: Pre-calc BEFORE task

        tasks.add(SimulationTask.builder()
            .algorithm(algorithm)
            .candles(candles)
            .build());
    }

    // Execute in parallel with Virtual Threads
    ParallelSimulationExecutor executor = new ParallelSimulationExecutor(
        result -> persister.persist(result),  // Save to MongoDB
        50  // Report every 50 simulations
    );

    List<SimulationTask.Result> results = executor.executeAll(tasks);
}
```

**MongoDB Persistence**:
- Collection naming: `TrendFollowing-<TRADING_PAIR>` (e.g., `TrendFollowing-BTCUSDT`)
- Batch persistence for performance (flush every 100 results)
- Enabled by default (use `false` arg to disable)

**Performance**:
- ~1000 simulations complete in minutes (vs hours with sequential)
- Virtual Threads: Lightweight, millions of threads possible
- CPU-efficient: No thread pool limits

---

## Architecture Decisions

### Module Structure
```
bot-core/          → Core models, interfaces, indicators (no deps)
bot-algorithms/    → Algorithm implementations (depends on bot-core)
bot-backtest/      → Backtesting engine, permutation (depends on core + algorithms)
bot-persistence/   → MongoDB repositories (depends on core)
bot-production/    → Spring Boot REST API (depends on all)
```

### Immutability Pattern
- All configs: `@Value @Builder` (Lombok)
- All decisions: Sealed interfaces with records
- State: Mutable `@Data` for performance (frequent updates during backtest)

### Indicator Design
- Static utility classes (no state)
- Accept `List<Candle>` + parameters
- Return `List<T>` where T = BigDecimal | MACDResult | SwingPoint
- Null-safe: Return empty lists for insufficient data

### Logging Strategy
- Algorithm logic: DEBUG level (detailed entry/exit reasons)
- Permutation progress: INFO level (every 50 simulations)
- Critical events: INFO level (golden cross, death cross, TP hits)
- Errors: ERROR level with full stack traces

---

## Testing Recommendations

### Single Simulation Testing
```bash
# Default: BTCUSDT-1-365.txt
java -cp bot-backtest/target/bot-backtest.jar \
  com.tradingbot.backtest.runners.TrendFollowingSingleRunner

# Custom pair
java -cp bot-backtest/target/bot-backtest.jar \
  com.tradingbot.backtest.runners.TrendFollowingSingleRunner ETHUSDT

# Custom file
java -cp bot-backtest/target/bot-backtest.jar \
  com.tradingbot.backtest.runners.TrendFollowingSingleRunner BTCUSDT-5-90.txt
```

### Permutation Mode Testing
```bash
# Default: BTCUSDT with MongoDB
java -cp bot-backtest/target/bot-backtest.jar \
  com.tradingbot.backtest.runners.TrendFollowingPermutationRunner

# Disable MongoDB
java -cp bot-backtest/target/bot-backtest.jar \
  com.tradingbot.backtest.runners.TrendFollowingPermutationRunner BTCUSDT false

# Different pair
java -cp bot-backtest/target/bot-backtest.jar \
  com.tradingbot.backtest.runners.TrendFollowingPermutationRunner ETHUSDT
```

### Expected Output
```
========================================
  TRENDFOLLOWING SIMULATION RESULTS
========================================

Algorithm:        TrendFollowing
Trading Pair:     BTCUSDT

--- PROFITABILITY ---
Initial Balance:  $10000.00
Final Cash:       $8500.00
Final Equity:     $12500.00
Profit (Absolute): $2500.00
Profit (%):       25.00%

--- TRADING ACTIVITY ---
Total Trades:     15
Open Positions:   1

--- DRAWDOWN ---
Max Position DD:  -12.50%
Max Portfolio DD: -8.75%
========================================
```

---

## Future Enhancements

### Potential Improvements
1. **Multi-Position Support**: Allow N concurrent positions with correlation checks
2. **Adaptive Parameters**: Dynamic EMA/MACD periods based on volatility
3. **Risk-Based Sizing**: Adjust position size based on ATR or recent drawdown
4. **Commission Modeling**: Add realistic fees (0.1% maker, 0.2% taker)
5. **Slippage Simulation**: Model market impact for large orders
6. **Walk-Forward Analysis**: Rolling window optimization
7. **Ensemble Methods**: Combine multiple parameter sets with voting

### Production Considerations
1. **Real-Time Data**: WebSocket integration for live candle feeds
2. **Order Management**: Retry logic, partial fills, order book depth
3. **Rate Limiting**: Exchange API throttling
4. **State Persistence**: Save algorithm state to database for recovery
5. **Monitoring**: Prometheus metrics, Grafana dashboards
6. **Alerting**: Telegram/Discord notifications for entries/exits

---

## Questions Answered

**User's final question**: "czy masz jakies jeszce pytania?"

**Answer**: No additional questions. All implementation details were clarified through Q1-Q8, and the full TrendFollowing system (Milestones 1-4) has been successfully implemented, tested, and documented.

---

**Document Version**: 1.0
**Last Updated**: 2025-12-28
**Implementation Status**: Complete (Milestones 1-4)
**Git Commit**: d4d91b6 (Milestones 1-3), Runners pending commit
