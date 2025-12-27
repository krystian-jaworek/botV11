package com.tradingbot.production.exchange;

import com.tradingbot.core.models.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

/**
 * Service for fetching market data from Bybit.
 *
 * Bybit API documentation: https://bybit-exchange.github.io/docs/v5/intro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BybitMarketDataService {

    private final WebClient.Builder webClientBuilder;

    @Value("${bybit.api.base-url}")
    private String baseUrl;

    @Value("${bybit.api.use-testnet}")
    private boolean useTestnet;

    @Value("${bybit.api.testnet-url}")
    private String testnetUrl;

    /**
     * Get current candle (last 1-minute candle) for a trading pair.
     *
     * In production, this would call:
     * GET /v5/market/kline?category=spot&symbol=BTCUSDT&interval=1&limit=1
     */
    public Candle getCurrentCandle(String tradingPair) {
        try {
            String url = useTestnet ? testnetUrl : baseUrl;

            // TODO: Implement actual API call
            // For now, return a mock candle
            return createMockCandle(tradingPair);

        } catch (Exception e) {
            log.error("Failed to get current candle for {}", tradingPair, e);
            return null;
        }
    }

    /**
     * Get current price for a trading pair.
     *
     * GET /v5/market/tickers?category=spot&symbol=BTCUSDT
     */
    public BigDecimal getCurrentPrice(String tradingPair) {
        try {
            Candle candle = getCurrentCandle(tradingPair);
            return candle != null ? candle.close() : BigDecimal.ZERO;

        } catch (Exception e) {
            log.error("Failed to get current price for {}", tradingPair, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Create mock candle for testing
     * TODO: Replace with actual Bybit API integration
     */
    private Candle createMockCandle(String tradingPair) {
        BigDecimal mockPrice = new BigDecimal("90000.00");

        return new Candle(
            mockPrice,
            mockPrice,
            mockPrice.multiply(new BigDecimal("1.001")),
            mockPrice.multiply(new BigDecimal("0.999")),
            System.currentTimeMillis()
        );
    }
}
