package com.tradingbot.core.models;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Trading pair configuration with exchange-specific precision rules.
 *
 * Each trading pair has:
 * - Price precision (decimal places for order price)
 * - Quantity precision (decimal places for order size)
 *
 * These precisions match exchange requirements (e.g., Binance futures).
 */
@Getter
public enum TradingPair {
    /**
     * Bitcoin/USDT
     * - Price: integer only (e.g., 95000, 96123)
     * - Quantity: 3 decimal places (e.g., 0.001, 0.523)
     */
    BTCUSDT("BTCUSDT", 0, 3),

    /**
     * Binance Coin/USDT
     * - Price: 2 decimal places (e.g., 612.45, 598.12)
     * - Quantity: 1 decimal place (e.g., 0.1, 5.3)
     */
    BNBUSDT("BNBUSDT", 2, 1),

    /**
     * Ethereum/USDT
     * - Price: 2 decimal places (e.g., 3456.78)
     * - Quantity: 3 decimal places (e.g., 0.123)
     */
    ETHUSDT("ETHUSDT", 2, 3);

    private final String symbol;
    private final int pricePrecision;
    private final int quantityPrecision;

    TradingPair(String symbol, int pricePrecision, int quantityPrecision) {
        this.symbol = symbol;
        this.pricePrecision = pricePrecision;
        this.quantityPrecision = quantityPrecision;
    }

    /**
     * Round price to exchange precision.
     * Uses HALF_UP rounding mode.
     *
     * @param price Raw price to round
     * @return Price rounded to exchange precision
     */
    public BigDecimal roundPrice(BigDecimal price) {
        if (price == null) {
            return null;
        }
        return price.setScale(pricePrecision, RoundingMode.HALF_UP);
    }

    /**
     * Round quantity to exchange precision.
     * Uses DOWN rounding mode to avoid exceeding available balance.
     *
     * @param quantity Raw quantity to round
     * @return Quantity rounded to exchange precision
     */
    public BigDecimal roundQuantity(BigDecimal quantity) {
        if (quantity == null) {
            return null;
        }
        return quantity.setScale(quantityPrecision, RoundingMode.DOWN);
    }

    /**
     * Get minimum quantity step size.
     *
     * @return Minimum quantity increment (e.g., 0.001 for BTCUSDT)
     */
    public BigDecimal getMinQuantityStep() {
        return BigDecimal.ONE.scaleByPowerOfTen(-quantityPrecision);
    }

    /**
     * Get minimum price step size.
     *
     * @return Minimum price increment (e.g., 1 for BTCUSDT)
     */
    public BigDecimal getMinPriceStep() {
        return BigDecimal.ONE.scaleByPowerOfTen(-pricePrecision);
    }

    /**
     * Find trading pair by symbol string.
     *
     * @param symbol Symbol to search for (case-insensitive)
     * @return TradingPair if found
     * @throws IllegalArgumentException if symbol not found
     */
    public static TradingPair fromSymbol(String symbol) {
        for (TradingPair pair : values()) {
            if (pair.symbol.equalsIgnoreCase(symbol)) {
                return pair;
            }
        }
        throw new IllegalArgumentException("Unknown trading pair: " + symbol);
    }
}
