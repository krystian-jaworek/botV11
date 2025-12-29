# BotV11 - Trading Bot System

Multi-module Java 21 trading bot with backtesting and production capabilities.

## Modules

- **bot-core** - Domain models, interfaces, and utilities
- **bot-algorithms** - Trading algorithm implementations (GridBot, TrendFollowing)
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

### 3. TrendFollowing Algorithm (Multiple Timeframes)

Multi-timeframe trend following strategy with advanced exit management.

**Single Simulation:**
```bash
# Default config
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.TrendFollowingSingleRunner

# Custom file
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.TrendFollowingSingleRunner ETHUSDT-1-365.txt
```

**Permutation Mode (~1000 simulations):**
```bash
# Default (BTCUSDT-1-365.txt, MongoDB enabled)
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.TrendFollowingPermutationRunner

# Custom file
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.TrendFollowingPermutationRunner ETHUSDT

# Disable MongoDB
java -cp bot-backtest/target/bot-backtest.jar com.tradingbot.backtest.runners.TrendFollowingPermutationRunner BTCUSDT false
```

**Strategy Overview:**

*Entry Conditions (ALL must be true):*
1. Golden cross on primary timeframe (EMA fast crosses above slow)
2. Golden cross OR fast > slow on secondary timeframe
3. N higher lows detected (swing-based detection)
4. MACD histogram growing for M periods on BOTH timeframes

*Exit Strategies (priority order):*
1. **Stop Loss** - Fixed % from entry (15-25%)
2. **Trailing Stop** - Activates at profit threshold (50-75%)
3. **Death Cross** - Fast EMA crosses below slow EMA
4. **Take Profit Levels** - Partial closes (e.g., TP1: 200%, TP2: 300%, TP3: 500%)

*Hybrydowy Trailing Stop:*
- When trailing stop exceeds a TP level, dynamically adjust that TP higher
- Locks in more profit while allowing further upside
- Buffer: TP = trailing price × 1.10

**Parameter Ranges (reduced):**
- EMA fast primary: [50, 100] (2 values)
- EMA slow primary: [150, 200, 250] (3 values)
- EMA fast secondary: [20, 30] (2 values)
- EMA slow secondary: [50, 100] (2 values)
- MACD: [12,16] × [26,32] × [9,12]
- Higher lows: [3, 4, 5] periods
- Swing detection: [5, 7] periods
- Histogram growth: [2, 3] periods
- TP levels: Conservative [200,300,500] or Aggressive [250,400,700]
- Stop loss: [15%, 20%, 25%]
- Trailing activation: [50%, 75%]
- Trailing distance: [20%, 25%]

**Total: ~1000 valid combinations**

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
2. **Top 10 configurations** - includes complete TrendFollowing parameters
3. **Top 10 from MongoDB** - includes MongoDB document IDs (if enabled)
4. **Best configuration details** - complete metrics and config ID

**MongoDB Collections:**
- Results stored in: `TrendFollowing-<PAIR>` (e.g., `TrendFollowing-BTCUSDT`)

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
