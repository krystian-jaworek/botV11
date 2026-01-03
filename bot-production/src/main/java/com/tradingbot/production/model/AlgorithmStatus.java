package com.tradingbot.production.model;

/**
 * Status of an algorithm instance
 */
public enum AlgorithmStatus {
    /**
     * Algorithm is actively running (executed every minute by scheduler)
     */
    RUNNING,

    /**
     * Algorithm has been stopped manually by user
     */
    STOPPED,

    /**
     * Algorithm has been automatically stopped due to errors
     * (3 or more consecutive errors)
     */
    ERROR
}
