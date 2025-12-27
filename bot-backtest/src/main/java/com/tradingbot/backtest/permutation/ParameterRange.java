package com.tradingbot.backtest.permutation;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a range for a parameter in permutation testing.
 *
 * @param <T> Type of the parameter (Integer, BigDecimal, etc.)
 */
@Value
@Builder
public class ParameterRange<T> {
    String parameterName;
    T min;
    T max;
    T step;

    /**
     * Generate all values in this range
     */
    public List<T> generateValues() {
        List<T> values = new ArrayList<>();

        if (min instanceof Integer) {
            int current = (Integer) min;
            int maxInt = (Integer) max;
            int stepInt = (Integer) step;

            while (current <= maxInt) {
                values.add((T) Integer.valueOf(current));
                current += stepInt;
            }
        } else if (min instanceof BigDecimal) {
            BigDecimal current = (BigDecimal) min;
            BigDecimal maxDecimal = (BigDecimal) max;
            BigDecimal stepDecimal = (BigDecimal) step;

            while (current.compareTo(maxDecimal) <= 0) {
                values.add((T) current);
                current = current.add(stepDecimal);
            }
        } else {
            throw new IllegalArgumentException("Unsupported parameter type: " + min.getClass());
        }

        return values;
    }

    /**
     * Create an integer range
     */
    public static ParameterRange<Integer> intRange(String name, int min, int max, int step) {
        return ParameterRange.<Integer>builder()
            .parameterName(name)
            .min(min)
            .max(max)
            .step(step)
            .build();
    }

    /**
     * Create a BigDecimal range
     */
    public static ParameterRange<BigDecimal> decimalRange(String name, String min, String max, String step) {
        return ParameterRange.<BigDecimal>builder()
            .parameterName(name)
            .min(new BigDecimal(min))
            .max(new BigDecimal(max))
            .step(new BigDecimal(step))
            .build();
    }
}
