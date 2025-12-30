# BotV11 - Trading Bot System

Multi-module Java 21 trading bot with backtesting and production capabilities.

## Modules

- **bot-core** - Domain models, interfaces, and utilities
- **bot-algorithms** - Trading algorithm implementations (GridBot, DynamicGrid)
- **bot-backtest** - Backtesting engine with single and permutation modes
- **bot-persistence** - MongoDB persistence layer
- **bot-production** - Spring Boot production system with REST API

## Requirements

- Java 21
- Maven 3.9+
- MongoDB 5.0+ (for permutation mode and production)

## Build

```bash
mvn clean package
```

## Usage

### 1. Single Simulation (Backtest)

Run a single backtest with hardcoded GridBot parameters:
- 20 grid levels
- 1% distance between levels
- 2% take profit
- 70% portfolio allocation
- $10,000 initial capital

```bash
java -jar bot-backtest/target/bot-backtest.jar BTCUSDT-1-1.txt
```

**Input file format** (`src/main/resources/BTCUSDT-1-1.txt`):
```
{"open": 90000.00, "close": 90050.00, "high": 90100.00, "low": 89950.00, "timestamp": "1735334400000"}
{"open": 90050.00, "close": 89900.00, "high": 90080.00, "low": 89850.00, "timestamp": "1735334460000"}
...
```

File naming convention: `<PAIR>-<INTERVAL_MINUTES>-<DAYS>.txt`

**Output:**
- Detailed logging for each position open/close
- Portfolio state after each action
- Final metrics summary (profit, drawdown, etc.)

### 2. GridBot Permutation Mode (Parameter Optimization)

Run 300 parallel GridBot simulations with different parameter combinations.

**Note:** MongoDB persistence is **enabled by default**. Results are saved to `GridBot-<PAIR>` collection.

```bash
# Default (BTCUSDT-1-365.txt, MongoDB enabled)
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.GridBotPermutationRunner

# With trading pair (uses <PAIR>-1-365.txt, MongoDB enabled)
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.GridBotPermutationRunner ETHUSDT

# With custom file (MongoDB enabled)
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.GridBotPermutationRunner BTCUSDT-5-90.txt

# Disable MongoDB persistence
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.GridBotPermutationRunner BTCUSDT false
```

**Parameter ranges** (default):
- Grid levels: 10, 15, 20, 25, 30 (5 values)
- Grid distance: 0.5%, 1.0%, 1.5%, 2.0% (4 values)
- Take profit: 1.0%, 1.5%, 2.0%, 2.5%, 3.0% (5 values)
- Portfolio allocation: 60%, 70%, 80% (3 values)

**Total:** 5 × 4 × 5 × 3 = **300 simulations**

**Features:**
- Java 21 Virtual Threads for parallel execution
- Progress reporting every 50 simulations
- Batch MongoDB persistence (1000 results or 10s interval)
- Top 10 configurations summary with **algorithm parameters**
- Best/worst/average/median statistics
- **MongoDB ID display** for each top result (when MongoDB enabled)

**Output:**
The permutation runner displays:
1. **Summary statistics** - total simulations, success rate, profit stats
2. **Top 10 configurations** - includes algorithm parameters for each result
3. **Top 10 from MongoDB** - includes MongoDB document IDs (if enabled)
4. **Best configuration details** - complete metrics and config ID

**MongoDB Collections:**
- Results stored in: `GridBot-<PAIR>` (e.g., `GridBot-BTCUSDT`)

### 3. DynamicGrid Trading (Advanced Grid Strategy)

Advanced grid trading with dynamic level shifting and FIFO position management.

**Single Simulation:**
```bash
# Default config
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.DynamicGridSingleRunner

# Custom file
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.DynamicGridSingleRunner ETHUSDT-1-365.txt
```

**Permutation Mode (~1,000 simulations):**
```bash
# Default (BTCUSDT-1-365.txt, MongoDB enabled)
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.DynamicGridPermutationRunner

# Custom file
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.DynamicGridPermutationRunner ETHUSDT

# Disable MongoDB
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.DynamicGridPermutationRunner BTCUSDT false
```

**Strategy Overview:**

Unlike static GridBot, DynamicGrid adapts to market conditions:

1. **Bottom Breach Mechanism**: When price falls below lowest grid level:
   - Closes oldest position (FIFO)
   - Adds new buy level below
   - Removes old bottom level
   - Grid "shifts down" following price

2. **Top Expansion Mechanism**: When price exceeds top level trigger:
   - Closes oldest position (FIFO)
   - Expands grid upward
   - Realizes profits from old positions

3. **FIFO Position Management**:
   - Tracks all positions chronologically
   - Always closes oldest first
   - Prevents accumulation of underwater positions

4. **Individual Take Profits**:
   - Each position has its own TP level
   - TP = entry_price × (1 + tp_percent)
   - Positions close independently when TP hit

**Parameter Ranges (default):**
- Grid levels: 10, 15, 20 (3 values)
- Grid spacing: 0.5%, 1.0%, 1.5%, 2.0% (4 values)
- Take profit: 1.0%, 1.5%, 2.0%, 2.5%, 3.0% (5 values)
- Position size: 5%, 10%, 15% (3 values)
- Top trigger: 0.3%, 0.5%, 0.7% (3 values)
- Fixed sizing: [dynamic, fixed] (2 values)

**Total:** 3 × 4 × 5 × 3 × 3 × 2 = **1,080 simulations**

**Features:**
- Java 21 Virtual Threads for parallel execution
- Progress reporting every 50 simulations
- Batch MongoDB persistence (1000 results or 10s interval)
- Top 10 configurations summary with **algorithm parameters**
- Best/worst/average/median statistics
- **MongoDB ID display** for each top result (when MongoDB enabled)

**Output:**
The permutation runner displays:
1. **Summary statistics** - total simulations, success rate, profit stats
2. **Top 10 configurations** - includes algorithm parameters for each result
3. **Top 10 with MongoDB IDs** - matches current results with MongoDB IDs
4. **Best configuration details** - complete metrics and config ID

**MongoDB Collections:**
- Results stored in: `DynamicGrid-<PAIR>` (e.g., `DynamicGrid-BTCUSDT`)

### 4. Production System (Spring Boot)

Start the production trading system:

```bash
java -jar bot-production/target/bot-production.jar
```

**Configuration** (`application.yml` or environment variables):

```yaml
mongodb:
  connection-string: mongodb://admin:password@localhost:27017/
  database: botV11

bybit:
  api:
    use-testnet: false
    api-key: ${BYBIT_API_KEY}
    api-secret: ${BYBIT_API_SECRET}

trading:
  scheduler:
    enabled: true
    interval-ms: 60000  # 1 minute

debug:
  event-sourcing:
    enabled: false
```

**REST API:**

```bash
# Start algorithm
curl -X POST http://localhost:8080/api/v1/algorithms/start \
  -H "Content-Type: application/json" \
  -d '{
    "algorithmName": "GridBot",
    "tradingPair": "BTCUSDT",
    "initialCapital": 1000,
    "parameters": {
      "gridLevels": 20,
      "gridDistancePercent": 1.0,
      "takeProfitPercent": 2.0,
      "portfolioAllocationPercent": 70.0
    }
  }'

# List active algorithms
curl http://localhost:8080/api/v1/algorithms/active

# Get algorithm status
curl http://localhost:8080/api/v1/algorithms/{algorithmId}

# Stop algorithm
curl -X POST http://localhost:8080/api/v1/algorithms/{algorithmId}/stop

# Health check
curl http://localhost:8080/api/v1/algorithms/health
```

**Features:**
- Automatic state recovery on restart
- Scheduled execution every 1 minute
- MongoDB state persistence
- Event sourcing in DEBUG mode
- Graceful shutdown

## Architecture

### GridBot Algorithm (LONG only)

**Strategy:**
1. Create static grid of buy levels below initial price
2. When price drops to a grid level → BUY
3. When price rises by take profit % from entry → SELL

**Grid Calculation:**
- Level 0: initialPrice
- Level 1: initialPrice × (1 - distance%)
- Level 2: initialPrice × (1 - distance%)²
- ...

**Example** (20 levels, 1% distance, initial price $90,000):
- Level 0: $90,000
- Level 1: $89,100
- Level 2: $88,209
- ...
- Level 19: $81,705

### DynamicGrid Algorithm (LONG only)

**Strategy:**
1. Create dynamic grid of buy/sell levels around initial price
2. When price drops to buy level → OPEN position
3. When price reaches position's TP → CLOSE with profit
4. When price breaches bottom → SHIFT grid down (close oldest, add new level)
5. When price exceeds top trigger → EXPAND grid up (close oldest)

**Grid Initialization:**
- Total levels: N (e.g., 10)
- Levels below start: floor(N/2) - buy orders
- Level at start: neutral
- Levels above start: remaining - sell targets
- Spacing: geometric (e.g., 1% between levels)

**Bottom Breach Flow:**
```
Price < bottom_level:
  1. Close oldest position (FIFO)
  2. new_bottom = bottom × (1 - spacing%)
  3. Add buy level at new_bottom
  4. Remove old bottom level
  5. Repeat if price still below
```

**Top Expansion Flow:**
```
Price > top × (1 + trigger%):
  1. Close oldest position (FIFO)
  2. new_top = top × (1 + spacing%)
  3. Add sell level at new_top
```

**FIFO Queue:**
- Positions tracked chronologically
- Oldest position always closed first
- Prevents accumulation of old positions
- Natural profit-taking mechanism

**Example** (10 levels, 1% spacing, $90,000 start):
- Levels: $81,707 ... $89,109 | $90,000 | $90,900 ... $99,471
- Bottom breach at $81,500 → close oldest, add $80,890
- Top expansion at $99,970 → close oldest, add $100,471

### Simulation Interruption

Simulation stops if:
1. Equity drops below 25% of maximum equity
2. Equity drops below 25% of initial balance

Interrupted simulations are marked as failed.

### Metrics

**Profit:**
- Absolute profit (USD)
- Percentage profit (%)

**Equity:**
- Max/Min equity (with open positions)
- Max/Min cash balance (without open positions)

**Drawdown:**
- Max position drawdown (% drop in single position)
- Max portfolio drawdown (% drop from peak equity)

## MongoDB

**Connection:**
```bash
mongodb://admin:password@localhost:27017/
```

**Database:** `botV11`

**Collections:**
- `GridBot-BTCUSDT` - Simulation results for GridBot on BTCUSDT
- `GridBot-ETHUSDT` - Simulation results for GridBot on ETHUSDT
- `DynamicGrid-BTCUSDT` - Simulation results for DynamicGrid on BTCUSDT
- `DynamicGrid-ETHUSDT` - Simulation results for DynamicGrid on ETHUSDT
- `algorithm_states` - Active algorithm states (production)
- `algorithm-events` - Event sourcing logs (DEBUG mode)

## Development

### Add Data Files

Place candle data files in `bot-backtest/src/main/resources/`:

```
bot-backtest/
└── src/main/resources/
    ├── BTCUSDT-1-365.txt    # BTC, 1-minute candles, 365 days
    ├── ETHUSDT-1-365.txt    # ETH, 1-minute candles, 365 days
    └── BTCUSDT-5-90.txt     # BTC, 5-minute candles, 90 days
```

### Run Tests

```bash
mvn test
```

### Build Docker Image

```bash
# Production system
docker build -t trading-bot-production -f bot-production/Dockerfile .

# Run
docker run -p 8080:8080 \
  -e MONGODB_CONNECTION_STRING=mongodb://... \
  -e BYBIT_API_KEY=... \
  -e BYBIT_API_SECRET=... \
  trading-bot-production
```

## Performance

**Permutation Mode:**
- 300 simulations in ~2 minutes (M1 Mac, single core equivalent)
- Virtual Threads: ~2-3 simulations/second per core
- Batch MongoDB inserts: ~50x faster than individual inserts

**Production System:**
- Scheduled execution: 1 minute intervals
- State save: <10ms per algorithm
- Recovery: <100ms per algorithm

## Future Enhancements

1. **Real Bybit API Integration**
   - HMAC signature generation
   - WebSocket for real-time data
   - Rate limiting with retry logic

2. **Additional Algorithms**
   - Moving Average Crossover
   - RSI Strategy
   - Bollinger Bands

3. **Risk Management**
   - Global stop-loss
   - Max drawdown triggers
   - Position sizing

4. **Monitoring**
   - Grafana dashboards
   - Prometheus metrics
   - Discord/Telegram alerts

## License

Proprietary
