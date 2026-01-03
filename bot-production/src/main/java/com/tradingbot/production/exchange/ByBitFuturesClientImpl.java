package com.tradingbot.production.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingbot.core.models.*;
import com.tradingbot.production.model.LiveFilledOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Implementation of ByBit Futures API client using V5 API.
 * Uses HMAC SHA256 for request signing.
 *
 * API Docs: https://bybit-exchange.github.io/docs/v5/intro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ByBitFuturesClientImpl implements ByBitFuturesClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${bybit.api.base-url}")
    private String baseUrl;

    @Value("${bybit.api.testnet-url}")
    private String testnetUrl;

    @Value("${bybit.api.use-testnet}")
    private boolean useTestnet;

    private static final String CATEGORY_LINEAR = "linear";  // USDT perpetual futures
    private static final int RECV_WINDOW = 5000;  // 5 seconds

    @Override
    public Candle getLastCompletedCandle(TradingPair pair, String apiKey, String apiSecret) {
        try {
            String url = getBaseUrl() + "/v5/market/kline";
            String symbol = pair.getSymbol();

            String response = webClientBuilder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path(url)
                    .queryParam("category", CATEGORY_LINEAR)
                    .queryParam("symbol", symbol)
                    .queryParam("interval", "1")  // 1 minute
                    .queryParam("limit", "1")
                    .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseKlineResponse(response);

        } catch (Exception e) {
            log.error("Failed to get last completed candle for {}", pair, e);
            throw new RuntimeException("Failed to get candle", e);
        }
    }

    @Override
    public BigDecimal getCurrentMarkPrice(TradingPair pair) {
        try {
            String url = getBaseUrl() + "/v5/market/tickers";
            String symbol = pair.getSymbol();

            String response = webClientBuilder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path(url)
                    .queryParam("category", CATEGORY_LINEAR)
                    .queryParam("symbol", symbol)
                    .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseTickerResponse(response);

        } catch (Exception e) {
            log.error("Failed to get mark price for {}", pair, e);
            throw new RuntimeException("Failed to get mark price", e);
        }
    }

    @Override
    public BigDecimal getAccountBalance(String apiKey, String apiSecret) {
        try {
            String timestamp = String.valueOf(Instant.now().toEpochMilli());
            String recvWindow = String.valueOf(RECV_WINDOW);

            // Build query string for signature
            String queryString = "accountType=UNIFIED&coin=USDT";
            String signaturePayload = timestamp + apiKey + recvWindow + queryString;
            String signature = generateSignature(signaturePayload, apiSecret);

            String url = getBaseUrl() + "/v5/account/wallet-balance";

            String response = webClientBuilder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path(url)
                    .queryParam("accountType", "UNIFIED")
                    .queryParam("coin", "USDT")
                    .build())
                .header("X-BAPI-API-KEY", apiKey)
                .header("X-BAPI-SIGN", signature)
                .header("X-BAPI-TIMESTAMP", timestamp)
                .header("X-BAPI-RECV-WINDOW", recvWindow)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseBalanceResponse(response);

        } catch (Exception e) {
            log.error("Failed to get account balance", e);
            throw new RuntimeException("Failed to get account balance", e);
        }
    }

    @Override
    public LiveFilledOrder placeMarketOrder(
        TradingPair pair,
        OrderSide side,
        BigDecimal quantity,
        String apiKey,
        String apiSecret
    ) throws ByBitApiException {
        try {
            String timestamp = String.valueOf(Instant.now().toEpochMilli());
            String recvWindow = String.valueOf(RECV_WINDOW);

            // Round quantity to trading pair precision
            BigDecimal roundedQty = pair.roundQuantity(quantity);

            // Build request body
            String requestBody = String.format(
                "{\"category\":\"%s\",\"symbol\":\"%s\",\"side\":\"%s\",\"orderType\":\"Market\",\"qty\":\"%s\"}",
                CATEGORY_LINEAR,
                pair.getSymbol(),
                side.name(),
                roundedQty.toPlainString()
            );

            // Generate signature: timestamp + apiKey + recvWindow + requestBody
            String signaturePayload = timestamp + apiKey + recvWindow + requestBody;
            String signature = generateSignature(signaturePayload, apiSecret);

            String url = getBaseUrl() + "/v5/order/create";

            log.info("Placing {} market order for {} {}", side, roundedQty, pair.getSymbol());

            String response = webClientBuilder.build()
                .post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-BAPI-API-KEY", apiKey)
                .header("X-BAPI-SIGN", signature)
                .header("X-BAPI-TIMESTAMP", timestamp)
                .header("X-BAPI-RECV-WINDOW", recvWindow)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseOrderResponse(response, pair, side, roundedQty);

        } catch (Exception e) {
            log.error("Failed to place market order: {} {} {}", side, quantity, pair, e);
            throw new ByBitApiException("Failed to place order: " + e.getMessage(), e);
        }
    }

    /**
     * Generate HMAC SHA256 signature for ByBit API authentication
     */
    private String generateSignature(String payload, String apiSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                apiSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * Parse kline (candle) response from ByBit
     */
    private Candle parseKlineResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode list = root.path("result").path("list");

        if (list.isEmpty()) {
            throw new RuntimeException("No candle data in response");
        }

        // ByBit kline format: [timestamp, open, high, low, close, volume, turnover]
        JsonNode kline = list.get(0);

        return new Candle(
            new BigDecimal(kline.get(1).asText()),  // open
            new BigDecimal(kline.get(4).asText()),  // close
            new BigDecimal(kline.get(2).asText()),  // high
            new BigDecimal(kline.get(3).asText()),  // low
            kline.get(0).asLong()                   // timestamp
        );
    }

    /**
     * Parse ticker response to get mark price
     */
    private BigDecimal parseTickerResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode list = root.path("result").path("list");

        if (list.isEmpty()) {
            throw new RuntimeException("No ticker data in response");
        }

        String markPrice = list.get(0).path("markPrice").asText();
        return new BigDecimal(markPrice);
    }

    /**
     * Parse balance response to get USDT balance
     */
    private BigDecimal parseBalanceResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode list = root.path("result").path("list");

        if (list.isEmpty()) {
            throw new RuntimeException("No balance data in response");
        }

        JsonNode coin = list.get(0).path("coin").get(0);
        String availableBalance = coin.path("availableToWithdraw").asText();

        return new BigDecimal(availableBalance);
    }

    /**
     * Parse order response after placing an order
     */
    private LiveFilledOrder parseOrderResponse(
        String response,
        TradingPair pair,
        OrderSide side,
        BigDecimal quantity
    ) throws Exception {
        JsonNode root = objectMapper.readTree(response);

        int retCode = root.path("retCode").asInt();
        if (retCode != 0) {
            String retMsg = root.path("retMsg").asText();
            throw new ByBitApiException("Order failed: " + retMsg);
        }

        String orderId = root.path("result").path("orderId").asText();

        log.info("Order placed successfully: {}", orderId);

        // Note: For market orders, we need to query order details to get exact fill price
        // For now, return with estimated data (in production, add GET /v5/order/realtime)

        return LiveFilledOrder.builder()
            .orderId(orderId)
            .tradingPair(pair.getSymbol())
            .side(side)
            .quantity(quantity)
            .price(BigDecimal.ZERO)  // TODO: Get actual fill price from order status
            .timestamp(Instant.now().toEpochMilli())
            .build();
    }

    private String getBaseUrl() {
        return useTestnet ? testnetUrl : baseUrl;
    }
}
