package com.tradingbot.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Response after creating algorithm instance
 */
@Data
@AllArgsConstructor
public class CreateAlgorithmResponse {
    private String instanceId;
    private BigDecimal initialBalance;
}
