package com.tradingbot.production.exchange;

/**
 * Exception thrown when ByBit API call fails
 */
public class ByBitApiException extends Exception {

    public ByBitApiException(String message) {
        super(message);
    }

    public ByBitApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
