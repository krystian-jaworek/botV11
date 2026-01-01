package com.tradingbot.persistence.config;

import org.bson.types.Decimal128;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.math.BigDecimal;

/**
 * Converter for serializing BigDecimal to MongoDB Decimal128 format.
 * This ensures BigDecimal values are stored as numeric types in MongoDB
 * instead of strings, allowing for proper numeric queries and aggregations.
 */
@WritingConverter
public class BigDecimalToDecimal128Converter implements Converter<BigDecimal, Decimal128> {

    @Override
    public Decimal128 convert(BigDecimal source) {
        return new Decimal128(source);
    }
}
