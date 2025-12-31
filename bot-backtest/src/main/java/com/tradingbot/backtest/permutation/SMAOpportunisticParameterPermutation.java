package com.tradingbot.backtest.permutation;

import com.tradingbot.algorithms.smaopportunistic.SMAOpportunisticConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameter permutation generator for SMA Opportunistic algorithm.
 *
 * Permutes:
 * - SMA period (number of candles)
 */
public class SMAOpportunisticParameterPermutation {

    private final List<Integer> smaPeriodValues;

    public SMAOpportunisticParameterPermutation(List<Integer> smaPeriodValues) {
        this.smaPeriodValues = smaPeriodValues;
    }

    /**
     * Create default permutation with SMA periods from 600 to 2040, step 60
     * (10h to 34h with 1m candles)
     */
    public static SMAOpportunisticParameterPermutation defaultPermutation() {
        List<Integer> periods = new ArrayList<>();
        for (int period = 600; period <= 2040; period += 60) {
            periods.add(period);
        }
        return new SMAOpportunisticParameterPermutation(periods);
    }

    /**
     * Create compact permutation for quick testing
     * Tests: 720, 1440, 2880 (12h, 24h, 48h with 1m candles)
     */
    public static SMAOpportunisticParameterPermutation compactPermutation() {
        return new SMAOpportunisticParameterPermutation(
            List.of(720, 1440, 2880)
        );
    }

    /**
     * Generate all configuration combinations
     */
    public List<SMAOpportunisticConfig> generateConfigurations() {
        List<SMAOpportunisticConfig> configs = new ArrayList<>();

        for (Integer smaPeriod : smaPeriodValues) {
            SMAOpportunisticConfig config = SMAOpportunisticConfig.builder()
                .smaPeriod(smaPeriod)
                .build();
            configs.add(config);
        }

        return configs;
    }

    /**
     * Get number of configurations that will be generated
     */
    public int getConfigurationCount() {
        return smaPeriodValues.size();
    }
}
