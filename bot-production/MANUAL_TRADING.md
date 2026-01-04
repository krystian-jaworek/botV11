# Manual Trading Controller - Przewodnik

Kontroler do ręcznych operacji na ByBit exchange. Działa tylko z profilem `local`.

## Konfiguracja

### 1. Ustaw zmienne środowiskowe

```bash
export BYBIT_API_KEY="your-bybit-api-key"
export BYBIT_API_SECRET="your-bybit-api-secret"
```

### 2. Uruchom aplikację z profilem local

```bash
mvn spring-boot:run -pl bot-production -Dspring-boot.run.profiles=local
```

lub

```bash
java -jar bot-production/target/bot-production-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
```

### 3. Konfiguracja w application-local.yaml

Możesz również ustawić credentials bezpośrednio w pliku:

```yaml
bybit:
  api-key: your-api-key-here
  api-secret: your-api-secret-here
  testnet: true  # true = testnet, false = mainnet PROD
```

**UWAGA:** Domyślnie używany jest testnet. Aby użyć mainnet, ustaw `bybit.testnet: false`

## Dostępne Endpointy

### 1. Sprawdź konfigurację

```bash
curl http://localhost:8080/api/manual/config
```

Odpowiedź:
```json
{
  "testnet": true,
  "apiKeyConfigured": true,
  "profile": "local"
}
```

### 2. Pobierz balans konta

```bash
curl http://localhost:8080/api/manual/balance
```

Odpowiedź:
```json
{
  "balance": 10000.50,
  "testnet": true,
  "currency": "USDT"
}
```

### 3. Pobierz ostatnią świecę

```bash
curl "http://localhost:8080/api/manual/candle?symbol=BTCUSDT"
```

Odpowiedź:
```json
{
  "symbol": "BTCUSDT",
  "candle": {
    "timestamp": 1704384000000,
    "open": 45000.0,
    "high": 45100.0,
    "low": 44900.0,
    "close": 45050.0,
    "volume": 123.45
  },
  "testnet": true
}
```

### 4. Otwórz pozycję (Market Order)

**LONG:**
```bash
curl -X POST http://localhost:8080/api/manual/open \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTCUSDT",
    "side": "LONG",
    "quantity": "0.001"
  }'
```

**SHORT:**
```bash
curl -X POST http://localhost:8080/api/manual/open \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTCUSDT",
    "side": "SHORT",
    "quantity": "0.001"
  }'
```

Odpowiedź:
```json
{
  "success": true,
  "order": {
    "orderId": "abc123",
    "tradingPair": "BTCUSDT",
    "side": "LONG",
    "quantity": 0.001,
    "price": 45050.0,
    "timestamp": 1704384060000,
    "fee": 0.05
  },
  "testnet": true
}
```

### 5. Zamknij pozycję

```bash
curl -X POST http://localhost:8080/api/manual/close \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTCUSDT",
    "side": "LONG",
    "quantity": "0.001"
  }'
```

**UWAGA:** `side` to strona pozycji którą zamykasz. Jeśli zamykasz pozycję LONG, podaj "LONG" - kontroler automatycznie złoży zlecenie SHORT aby zamknąć pozycję.

Odpowiedź:
```json
{
  "success": true,
  "order": {
    "orderId": "xyz789",
    "tradingPair": "BTCUSDT",
    "side": "SHORT",
    "quantity": 0.001,
    "price": 45100.0,
    "timestamp": 1704384120000,
    "fee": 0.05
  },
  "closedSide": "LONG",
  "testnet": true
}
```

## Dostępne Pary Tradingowe

Zgodnie z enumem `TradingPair`:
- `BTCUSDT`
- `ETHUSDT`
- `SOLUSDT`
- (dodaj więcej według potrzeby w `bot-core/src/main/java/com/tradingbot/core/models/TradingPair.java`)

## Bezpieczeństwo

**WAŻNE:**
- Ten kontroler działa TYLKO z profilem `local`
- NIE używaj tego kontrolera na produkcji
- Zawsze sprawdzaj czy używasz testnetu przed wykonaniem operacji
- Nie commituj pliku `application-local.yaml` z prawdziwymi credentials do git

## Testowanie na ByBit Testnet

1. Załóż konto na testnet: https://testnet.bybit.com
2. Wygeneruj API key i secret w ustawieniach
3. Testnet daje darmowe środki do testowania
4. URL API dla testnet: https://api-testnet.bybit.com

## Przykładowy Workflow

```bash
# 1. Sprawdź konfigurację
curl http://localhost:8080/api/manual/config

# 2. Sprawdź balans
curl http://localhost:8080/api/manual/balance

# 3. Sprawdź aktualną cenę
curl "http://localhost:8080/api/manual/candle?symbol=BTCUSDT"

# 4. Otwórz małą pozycję LONG
curl -X POST http://localhost:8080/api/manual/open \
  -H "Content-Type: application/json" \
  -d '{"symbol": "BTCUSDT", "side": "LONG", "quantity": "0.001"}'

# 5. Poczekaj chwilę...

# 6. Zamknij pozycję
curl -X POST http://localhost:8080/api/manual/close \
  -H "Content-Type: application/json" \
  -d '{"symbol": "BTCUSDT", "side": "LONG", "quantity": "0.001"}'

# 7. Sprawdź nowy balans
curl http://localhost:8080/api/manual/balance
```
