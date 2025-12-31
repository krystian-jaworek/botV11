package com.tradingbot.algorithms.smartdca;

import com.tradingbot.core.models.Candle;
import com.tradingbot.core.models.Position;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages profit-taking exits for Smart DCA algorithm.
 * Handles staged exits, technical exits, trailing stops, and breakeven stops.
 */
@Slf4j
public class ProfitManager {

    private final SmartDCAConfig config;

    public ProfitManager(SmartDCAConfig config) {
        this.config = config;
    }

    /**
     * Evaluate exit signals for the current position.
     *
     * @param position Current position from Portfolio (null if no position)
     * @param exitState Exit strategy state tracking
     * @param candles Historical candles for indicator calculation
     * @param currentCandle Current candle
     * @param ema200 Current EMA200 value (can be null)
     * @return List of exit signals (can be multiple for different reasons)
     */
    public List<ExitSignal> evaluateExits(Position position, PositionExitState exitState,
                                          List<Candle> candles, Candle currentCandle, BigDecimal ema200) {

        List<ExitSignal> exits = new ArrayList<>();

        if (position == null) {
            return exits;
        }

        BigDecimal currentPrice = currentCandle.close();
        BigDecimal profitPct = calculateProfitPct(position.getEntryPrice(), currentPrice);

        // Update peak price for trailing stop
        exitState.updatePeakPrice(currentPrice);

        // 1. Check breakeven stop
        ExitSignal breakevenExit = checkBreakevenStop(position, exitState, currentPrice, profitPct);
        if (breakevenExit != null) {
            exits.add(breakevenExit);
            return exits;  // Breakeven stop closes entire position
        }

        // 2. Check trailing stop
        ExitSignal trailingExit = checkTrailingStop(position, exitState, currentPrice, profitPct);
        if (trailingExit != null) {
            exits.add(trailingExit);
            return exits;  // Trailing stop closes entire remaining position
        }

        // 3. Check staged exits
        List<ExitSignal> stagedExits = checkStagedExits(position, exitState, currentPrice, profitPct);
        exits.addAll(stagedExits);

        // 4. Check technical exits
        BigDecimal rsi = TechnicalIndicators.calculateRSI(candles,
            config.getBuyConditions().getRsi().getPeriod());
        if (rsi != null) {
            List<ExitSignal> technicalExits = checkTechnicalExits(position, currentPrice, profitPct, rsi, ema200);
            exits.addAll(technicalExits);
        }

        return exits;
    }

    /**
     * Calculate profit percentage from entry price
     */
    private BigDecimal calculateProfitPct(BigDecimal entryPrice, BigDecimal currentPrice) {
        if (entryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(entryPrice)
            .divide(entryPrice, 8, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    /**
     * Check if breakeven stop should trigger
     */
    private ExitSignal checkBreakevenStop(Position position, PositionExitState exitState,
                                          BigDecimal currentPrice, BigDecimal profitPct) {
        if (!exitState.isBreakevenStopActive()) {
            return null;
        }

        BigDecimal avgEntry = position.getEntryPrice();

        // If price drops to or below breakeven, close entire position
        if (currentPrice.compareTo(avgEntry) <= 0) {
            log.info("Breakeven stop triggered: price {} at/below entry {}", currentPrice, avgEntry);
            return new ExitSignal(
                ExitType.BREAKEVEN_STOP,
                new BigDecimal("100"),  // Close entire position
                "Breakeven stop: price at entry"
            );
        }

        return null;
    }

    /**
     * Check if trailing stop should trigger
     */
    private ExitSignal checkTrailingStop(Position position, PositionExitState exitState,
                                         BigDecimal currentPrice, BigDecimal profitPct) {
        if (!exitState.isTrailingStopActive()) {
            return null;
        }

        // Determine current trailing distance based on profit level
        BigDecimal trailingDistance = getTrailingDistance(profitPct);
        if (trailingDistance == null) {
            return null;
        }

        // Calculate trigger price (peak - trailing distance)
        BigDecimal triggerPrice = exitState.getPeakPrice()
            .multiply(BigDecimal.ONE.subtract(trailingDistance.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)));

        // Check if current price has dropped below trigger
        if (currentPrice.compareTo(triggerPrice) <= 0) {
            log.info("Trailing stop triggered: price {} below trigger {} (peak: {}, distance: {}%)",
                currentPrice, triggerPrice, exitState.getPeakPrice(), trailingDistance);
            return new ExitSignal(
                ExitType.TRAILING_STOP,
                new BigDecimal("100"),  // Close entire remaining position
                String.format("Trailing stop: %.2f%% from peak", trailingDistance)
            );
        }

        return null;
    }

    /**
     * Get trailing distance percentage based on current profit
     */
    private BigDecimal getTrailingDistance(BigDecimal profitPct) {
        if (!config.getProfitManagement().getTrailingStop().isEnabled()) {
            return null;
        }

        for (SmartDCAConfig.ProfitManagement.TrailingStop.TrailingTier tier :
             config.getProfitManagement().getTrailingStop().getDynamicTiers()) {

            BigDecimal min = tier.getProfitRange().get(0);
            BigDecimal max = tier.getProfitRange().get(1);

            if (profitPct.compareTo(min) >= 0 && profitPct.compareTo(max) < 0) {
                return tier.getDistancePct();
            }
        }

        return null;
    }

    /**
     * Check staged exits based on profit milestones
     */
    private List<ExitSignal> checkStagedExits(Position position, PositionExitState exitState,
                                               BigDecimal currentPrice, BigDecimal profitPct) {
        List<ExitSignal> exits = new ArrayList<>();

        if (!config.getProfitManagement().getStagedExits().isEnabled()) {
            return exits;
        }

        List<SmartDCAConfig.ProfitManagement.StagedExits.ExitStage> stages =
            config.getProfitManagement().getStagedExits().getStages();

        for (int i = 0; i < stages.size(); i++) {
            SmartDCAConfig.ProfitManagement.StagedExits.ExitStage stage = stages.get(i);

            // Skip if already triggered
            if (exitState.isStageTriggered(i)) {
                continue;
            }

            // Check if profit threshold reached
            if (profitPct.compareTo(stage.getProfitThresholdPct()) >= 0) {
                log.info("Staged exit triggered: stage {} at +{}% profit, closing {}%",
                    i + 1, stage.getProfitThresholdPct(), stage.getClosePct());

                // Mark stage as triggered
                exitState.markStageTriggered(i);

                // Add exit signal
                exits.add(new ExitSignal(
                    ExitType.STAGED_EXIT,
                    stage.getClosePct(),
                    String.format("Staged exit: +%.0f%% profit", stage.getProfitThresholdPct())
                ));

                // Activate breakeven stop if configured
                if (Boolean.TRUE.equals(stage.getMoveStopToBreakeven())) {
                    exitState.activateBreakevenStop();
                    log.info("Breakeven stop activated at stage {}", i + 1);
                }

                // Activate trailing stop if configured
                if (Boolean.TRUE.equals(stage.getActivateTrailing())) {
                    exitState.activateTrailingStop();
                    log.info("Trailing stop activated at stage {}", i + 1);
                }
            }
        }

        return exits;
    }

    /**
     * Check technical exits based on RSI overbought conditions
     */
    private List<ExitSignal> checkTechnicalExits(Position position, BigDecimal currentPrice,
                                                  BigDecimal profitPct, BigDecimal rsi, BigDecimal ema200) {
        List<ExitSignal> exits = new ArrayList<>();

        if (!config.getProfitManagement().getTechnicalExits().isEnabled()) {
            return exits;
        }

        // Check extreme overbought exit
        SmartDCAConfig.ProfitManagement.TechnicalExits.TechnicalExitRule extreme =
            config.getProfitManagement().getTechnicalExits().getExtreme();

        if (rsi.compareTo(new BigDecimal(extreme.getRsiThreshold())) > 0 &&
            profitPct.compareTo(extreme.getMinProfitPct()) >= 0) {

            log.info("Technical exit (extreme): RSI {} > {}, profit {}% > {}%, closing {}%",
                rsi, extreme.getRsiThreshold(), profitPct, extreme.getMinProfitPct(), extreme.getClosePct());

            exits.add(new ExitSignal(
                ExitType.TECHNICAL_EXIT,
                extreme.getClosePct(),
                String.format("Extreme overbought: RSI %.0f", rsi)
            ));
        }

        // Check normal overbought exit
        SmartDCAConfig.ProfitManagement.TechnicalExits.TechnicalExitRule normal =
            config.getProfitManagement().getTechnicalExits().getNormal();

        if (rsi.compareTo(new BigDecimal(normal.getRsiThreshold())) > 0 &&
            profitPct.compareTo(normal.getMinProfitPct()) >= 0) {

            // Check EMA condition if required
            boolean emaConditionMet = true;
            if (Boolean.TRUE.equals(normal.getRequireAboveEma200()) && ema200 != null) {
                emaConditionMet = currentPrice.compareTo(ema200) > 0;
            }

            if (emaConditionMet) {
                log.info("Technical exit (normal): RSI {} > {}, profit {}% > {}%, closing {}%",
                    rsi, normal.getRsiThreshold(), profitPct, normal.getMinProfitPct(), normal.getClosePct());

                exits.add(new ExitSignal(
                    ExitType.TECHNICAL_EXIT,
                    normal.getClosePct(),
                    String.format("Overbought: RSI %.0f", rsi)
                ));
            }
        }

        return exits;
    }

    /**
     * Exit signal result
     */
    public static class ExitSignal {
        @Getter
        private final ExitType type;

        @Getter
        private final BigDecimal percentageToClose;

        @Getter
        private final String reason;

        public ExitSignal(ExitType type, BigDecimal percentageToClose, String reason) {
            this.type = type;
            this.percentageToClose = percentageToClose;
            this.reason = reason;
        }
    }

    public enum ExitType {
        STAGED_EXIT,
        TECHNICAL_EXIT,
        TRAILING_STOP,
        BREAKEVEN_STOP
    }
}
