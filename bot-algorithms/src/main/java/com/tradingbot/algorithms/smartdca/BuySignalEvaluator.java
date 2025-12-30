package com.tradingbot.algorithms.smartdca;

import com.tradingbot.core.models.Candle;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;

/**
 * Evaluates buy signals for Smart DCA algorithm.
 * Checks all entry conditions and determines appropriate position sizing tier.
 */
@Slf4j
public class BuySignalEvaluator {

    private final SmartDCAConfig config;

    public BuySignalEvaluator(SmartDCAConfig config) {
        this.config = config;
    }

    /**
     * Evaluate whether to buy and at what tier.
     *
     * @param candles Historical candles (most recent last)
     * @param currentCandle Current candle
     * @param lastBuyTimestamp Timestamp of last buy (0 if no previous buy)
     * @param lastBuyPrice Price of last buy (null if no previous buy)
     * @return BuySignal if conditions met, null otherwise
     */
    public BuySignal evaluate(List<Candle> candles, Candle currentCandle,
                              long lastBuyTimestamp, BigDecimal lastBuyPrice) {

        BigDecimal currentPrice = currentCandle.close();
        long currentTime = currentCandle.timestamp();

        // 1. Check cooldown
        if (!checkCooldown(currentTime, lastBuyTimestamp)) {
            log.debug("Buy blocked: cooldown period not elapsed");
            return null;
        }

        // 2. Calculate indicators
        BigDecimal rsi = TechnicalIndicators.calculateRSI(candles,
            config.getBuyConditions().getRsi().getPeriod());
        if (rsi == null) {
            log.debug("Buy blocked: insufficient data for RSI");
            return null;
        }

        BigDecimal ema200 = null;
        if (config.getBuyConditions().getEmaSupport().isEnabled()) {
            ema200 = TechnicalIndicators.calculateEMAFull(candles,
                config.getBuyConditions().getEmaSupport().getPeriod());
            if (ema200 == null) {
                log.debug("Buy blocked: insufficient data for EMA");
                return null;
            }
        }

        // Volume spike check DISABLED - candles don't contain volume data
        // If you have volume data, uncomment below to enable volume confirmation
        /*
        BigDecimal volumeMA = null;
        if (config.getBuyConditions().getVolume().isRequireConfirmation()) {
            volumeMA = TechnicalIndicators.calculateVolumeSMA(candles,
                config.getBuyConditions().getVolume().getMaPeriods());
            if (volumeMA == null) {
                log.debug("Buy blocked: insufficient data for Volume MA");
                return null;
            }
        }
        */

        BigDecimal localHigh = TechnicalIndicators.findHighestClose(candles,
            config.getBuyConditions().getPriceDrop().getLocalHighLookbackPeriods());
        if (localHigh == null) {
            log.debug("Buy blocked: insufficient data for local high");
            return null;
        }

        // 3. Check RSI oversold
        if (rsi.compareTo(new BigDecimal(config.getBuyConditions().getRsi().getOversoldThreshold())) >= 0) {
            log.debug("Buy blocked: RSI {} not oversold (threshold: {})",
                rsi, config.getBuyConditions().getRsi().getOversoldThreshold());
            return null;
        }

        // 4. Check price drop
        BigDecimal dropFromLocalHigh = TechnicalIndicators.calculatePriceDrop(currentPrice, localHigh);
        boolean priceDropOk = dropFromLocalHigh.compareTo(
            config.getBuyConditions().getPriceDrop().getMinFromLocalHighPct()) >= 0;

        if (lastBuyPrice != null) {
            BigDecimal dropFromLastBuy = TechnicalIndicators.calculatePriceDrop(currentPrice, lastBuyPrice);
            priceDropOk = priceDropOk || dropFromLastBuy.compareTo(
                config.getBuyConditions().getPriceDrop().getMinFromLastBuyPct()) >= 0;
        }

        if (!priceDropOk) {
            log.debug("Buy blocked: price drop insufficient (from high: {}%, from last: {})",
                dropFromLocalHigh,
                lastBuyPrice != null ? TechnicalIndicators.calculatePriceDrop(currentPrice, lastBuyPrice) : "N/A");
            return null;
        }

        // 5. Check volume spike - DISABLED (no volume data in candles)
        // If you have volume data, uncomment and update volumeMA calculation above
        /*
        if (config.getBuyConditions().getVolume().isRequireConfirmation()) {
            BigDecimal volumeRatio = currentCandle.volume()
                .divide(volumeMA, 8, java.math.RoundingMode.HALF_UP);
            if (volumeRatio.compareTo(config.getBuyConditions().getVolume().getSpikeMultiplier()) < 0) {
                log.debug("Buy blocked: volume spike insufficient (ratio: {}x, required: {}x)",
                    volumeRatio, config.getBuyConditions().getVolume().getSpikeMultiplier());
                return null;
            }
        }
        */

        // 6. Check EMA support proximity
        if (config.getBuyConditions().getEmaSupport().isEnabled()) {
            BigDecimal distanceFromEMA = TechnicalIndicators.calculateDistanceFromLevel(currentPrice, ema200);
            // Distance should be negative (below EMA) or within max proximity
            if (distanceFromEMA.compareTo(config.getBuyConditions().getEmaSupport().getMaxProximityPct()) > 0) {
                log.debug("Buy blocked: too far from EMA (distance: {}%, max: {}%)",
                    distanceFromEMA, config.getBuyConditions().getEmaSupport().getMaxProximityPct());
                return null;
            }
        }

        // 7. Determine position sizing tier
        SmartDCAConfig.PositionSizing.PositionTier selectedTier = selectTier(rsi, dropFromLocalHigh);
        if (selectedTier == null) {
            log.debug("Buy blocked: no tier matches conditions (RSI: {}, drop: {}%)",
                rsi, dropFromLocalHigh);
            return null;
        }

        log.info("Buy signal generated: tier={}, RSI={}, drop={}%",
            selectedTier.getName(), rsi, dropFromLocalHigh);

        return new BuySignal(selectedTier, rsi, dropFromLocalHigh);
    }

    /**
     * Check if cooldown period has elapsed
     */
    private boolean checkCooldown(long currentTime, long lastBuyTime) {
        if (lastBuyTime == 0) {
            return true;  // No previous buy
        }

        long hoursElapsed = (currentTime - lastBuyTime) / (1000 * 60 * 60);
        return hoursElapsed >= config.getBuyConditions().getCooldown().getMinHoursBetweenBuys();
    }

    /**
     * Select the appropriate tier based on RSI and price drop
     */
    private SmartDCAConfig.PositionSizing.PositionTier selectTier(BigDecimal rsi, BigDecimal priceDrop) {
        for (SmartDCAConfig.PositionSizing.PositionTier tier : config.getPositionSizing().getTiers()) {
            List<Integer> rsiRange = tier.getConditions().getRsiRange();
            List<BigDecimal> dropRange = tier.getConditions().getPriceDropRange();

            boolean rsiMatch = rsi.compareTo(new BigDecimal(rsiRange.get(0))) >= 0 &&
                             rsi.compareTo(new BigDecimal(rsiRange.get(1))) < 0;

            boolean dropMatch = priceDrop.compareTo(dropRange.get(0)) >= 0 &&
                              priceDrop.compareTo(dropRange.get(1)) < 0;

            if (rsiMatch && dropMatch) {
                return tier;
            }
        }
        return null;
    }

    /**
     * Result of buy signal evaluation
     */
    public static class BuySignal {
        @Getter
        private final SmartDCAConfig.PositionSizing.PositionTier tier;

        @Getter
        private final BigDecimal rsi;

        @Getter
        private final BigDecimal priceDrop;

        public BuySignal(SmartDCAConfig.PositionSizing.PositionTier tier,
                        BigDecimal rsi, BigDecimal priceDrop) {
            this.tier = tier;
            this.rsi = rsi;
            this.priceDrop = priceDrop;
        }
    }
}
