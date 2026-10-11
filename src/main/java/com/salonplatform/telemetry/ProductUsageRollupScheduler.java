package com.salonplatform.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductUsageRollupScheduler {

    private final ProductUsageRollupService rollupService;

    @Scheduled(cron = "${app.product-telemetry.rollup-cron:0 15 * * * *}")
    public void hourlyRollup() {
        try {
            rollupService.rollupRecentDays(3);
        } catch (Exception e) {
            log.warn("Product usage rollup failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${app.product-telemetry.retention-cron:0 30 3 * * *}")
    public void nightlyRetention() {
        try {
            rollupService.purgeOldRawEvents();
        } catch (Exception e) {
            log.warn("Product telemetry retention purge failed: {}", e.getMessage());
        }
    }

    @Async
    public void bootstrapRollup() {
        try {
            rollupService.rollupRecentDays(35);
        } catch (Exception e) {
            log.warn("Initial product usage rollup failed: {}", e.getMessage());
        }
    }
}
