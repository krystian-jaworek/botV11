package com.tradingbot.production.scheduler;

import com.tradingbot.production.model.AlgorithmInstanceDocument;
import com.tradingbot.production.model.AlgorithmStatus;
import com.tradingbot.production.repository.AlgorithmInstanceRepository;
import com.tradingbot.production.service.AlgorithmRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler for executing running algorithm instances every minute.
 * Runs at 5 seconds past every minute to ensure candle is completed on exchange.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trading.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class TradingSchedulerV2 {

    private final AlgorithmInstanceRepository instanceRepository;
    private final AlgorithmRuntimeService runtimeService;

    /**
     * Execute all running algorithms.
     * Cron: 5 seconds past every minute
     * Format: second minute hour day month weekday
     */
    @Scheduled(cron = "5 * * * * *")
    public void executeAllRunningAlgorithms() {
        List<AlgorithmInstanceDocument> runningInstances =
            instanceRepository.findByStatus(AlgorithmStatus.RUNNING);

        if (runningInstances.isEmpty()) {
            log.debug("No running algorithms");
            return;
        }

        log.info("Processing {} running algorithms", runningInstances.size());

        for (AlgorithmInstanceDocument instance : runningInstances) {
            try {
                log.debug("[{}] Executing iteration", instance.getName());
                runtimeService.executeIteration(instance.getId());

            } catch (Exception e) {
                log.error("[{}] Scheduler error: {}", instance.getName(), e.getMessage(), e);
                // RuntimeService handles error tracking and status updates
            }
        }

        log.debug("Trading cycle completed");
    }
}
