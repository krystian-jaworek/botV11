package com.tradingbot.production.api;

import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.OrderSide;
import com.tradingbot.core.models.TradingPair;
import com.tradingbot.production.exchange.ByBitFuturesClient;
import com.tradingbot.production.model.LiveFilledOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Manual trading controller for local development and testing.
 * Only active with 'local' Spring profile.
 *
 * Uses ByBit credentials from application-local.yaml
 */
@Slf4j
@RestController
@RequestMapping("/api/manual")
@RequiredArgsConstructor
@Profile("local")
public class ManualTradingController {

    private final ByBitFuturesClient bybitClient;

    @Value("${bybit.api-key}")
    private String apiKey;

    @Value("${bybit.api-secret}")
    private String apiSecret;

    @Value("${bybit.testnet:true}")
    private boolean testnet;

    /**
     * Get account balance
     * GET /api/manual/balance
     */
    @GetMapping("/balance")
    public ResponseEntity<?> getBalance() {
        try {
            log.info("Fetching account balance (testnet: {})", testnet);

            BigDecimal balance = bybitClient.getAccountBalance(apiKey, apiSecret);

            return ResponseEntity.ok(Map.of(
                "balance", balance,
                "testnet", testnet,
                "currency", "USDT"
            ));

        } catch (Exception e) {
            log.error("Failed to fetch balance", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get last completed candle
     * GET /api/manual/candle?symbol=BTCUSDT
     */
    @GetMapping("/candle")
    public ResponseEntity<?> getCandle(@RequestParam String symbol) {
        try {
            TradingPair pair = TradingPair.valueOf(symbol);
            log.info("Fetching last candle for {}", pair);

            Candle candle = bybitClient.getLastCompletedCandle(pair, apiKey, apiSecret);

            return ResponseEntity.ok(Map.of(
                "symbol", symbol,
                "candle", candle,
                "testnet", testnet
            ));

        } catch (Exception e) {
            log.error("Failed to fetch candle", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Open position (market order)
     * POST /api/manual/open
     * Body: {
     *   "symbol": "BTCUSDT",
     *   "side": "LONG",
     *   "quantity": "0.001"
     * }
     */
    @PostMapping("/open")
    public ResponseEntity<?> openPosition(@RequestBody OpenPositionRequest request) {
        try {
            log.info("Opening {} position for {} quantity: {}",
                request.side, request.symbol, request.quantity);

            TradingPair pair = TradingPair.valueOf(request.symbol);
            OrderSide side = OrderSide.valueOf(request.side);
            BigDecimal quantity = new BigDecimal(request.quantity);

            LiveFilledOrder order = bybitClient.placeMarketOrder(
                pair, side, quantity, apiKey, apiSecret
            );

            log.info("Position opened: {}", order);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "order", order,
                "testnet", testnet
            ));

        } catch (Exception e) {
            log.error("Failed to open position", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Close position (reverse market order)
     * POST /api/manual/close
     * Body: {
     *   "symbol": "BTCUSDT",
     *   "side": "LONG",  // side of position to close (will place opposite order)
     *   "quantity": "0.001"
     * }
     */
    @PostMapping("/close")
    public ResponseEntity<?> closePosition(@RequestBody ClosePositionRequest request) {
        try {
            log.info("Closing {} position for {} quantity: {}",
                request.side, request.symbol, request.quantity);

            TradingPair pair = TradingPair.valueOf(request.symbol);
            OrderSide positionSide = OrderSide.valueOf(request.side);
            BigDecimal quantity = new BigDecimal(request.quantity);

            // To close a LONG position, we need to place a SHORT order (and vice versa)
            OrderSide closeSide = positionSide == OrderSide.LONG ? OrderSide.SHORT : OrderSide.LONG;

            LiveFilledOrder order = bybitClient.placeMarketOrder(
                pair, closeSide, quantity, apiKey, apiSecret
            );

            log.info("Position closed: {}", order);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "order", order,
                "closedSide", positionSide,
                "testnet", testnet
            ));

        } catch (Exception e) {
            log.error("Failed to close position", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get configuration info
     * GET /api/manual/config
     */
    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        return ResponseEntity.ok(Map.of(
            "testnet", testnet,
            "apiKeyConfigured", apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-api-key-here"),
            "profile", "local"
        ));
    }

    // DTOs for requests
    public static class OpenPositionRequest {
        public String symbol;
        public String side;  // LONG or SHORT
        public String quantity;
    }

    public static class ClosePositionRequest {
        public String symbol;
        public String side;  // LONG or SHORT (side of position being closed)
        public String quantity;
    }
}
