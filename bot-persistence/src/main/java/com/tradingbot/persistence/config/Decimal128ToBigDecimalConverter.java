package com.tradingbot.persistence.config;

import org.bson.types.Decimal128;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.math.BigDecimal;

/**
 * Converter for deserializing MongoDB Decimal128 to BigDecimal.
 * This ensures Decimal128 values from MongoDB are properly converted
 * back to BigDecimal when reading documents.
 */
@ReadingConverter
public class Decimal128ToBigDecimalConverter implements Converter<Decimal128, BigDecimal> {

    @Override
    public BigDecimal convert(Decimal128 source) {
        return source.bigDecimalValue();
    }
}
