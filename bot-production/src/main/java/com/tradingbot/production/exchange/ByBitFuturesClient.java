package com.tradingbot.production.exchange;

import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.FilledOrder;
import com.tradingbot.core.models.OrderSide;
import com.tradingbot.core.models.TradingPair;

import java.math.BigDecimal;

/**
 * Client for interacting with ByBit Futures API.
 *
 * ByBit API Documentation: https://bybit-exchange.github.io/docs/v5/intro
 */
public interface ByBitFuturesClient {

    /**
     * Get latest completed 1-minute candle for a trading pair.
     *
     * API: GET /v5/market/kline
     * Category: linear (USDT perpetual futures)
     *
     * @param pair Trading pair
     * @param apiKey ByBit API key (for authentication)
     * @param apiSecret ByBit API secret (for authentication)
     * @return Latest completed 1-minute candle
     */
    Candle getLastCompletedCandle(TradingPair pair, String apiKey, String apiSecret);

    /**
     * Get current mark price for a trading pair.
     * Used for position valuation.
     *
     * API: GET /v5/market/tickers
     * Category: linear
     *
     * @param pair Trading pair
     * @return Current mark price
     */
    BigDecimal getCurrentMarkPrice(TradingPair pair);

    /**
     * Get account USDT balance for futures trading.
     *
     * API: GET /v5/account/wallet-balance
     * AccountType: UNIFIED (for USDT perpetual)
     *
     * @param apiKey ByBit API key
     * @param apiSecret ByBit API secret
     * @return Available USDT balance
     */
    BigDecimal getAccountBalance(String apiKey, String apiSecret);

    /**
     * Place a market order on ByBit Futures.
     *
     * API: POST /v5/order/create
     * Category: linear
     * OrderType: Market
     *
     * @param pair Trading pair
     * @param side BUY or SELL
     * @param quantity Quantity in base currency (e.g., BTC for BTCUSDT)
     * @param apiKey ByBit API key
     * @param apiSecret ByBit API secret
     * @return Filled order with execution details
     * @throws ByBitApiException if order fails
     */
    FilledOrder placeMarketOrder(
        TradingPair pair,
        OrderSide side,
        BigDecimal quantity,
        String apiKey,
        String apiSecret
    ) throws ByBitApiException;
}
