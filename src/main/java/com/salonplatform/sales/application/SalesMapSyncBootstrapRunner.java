package com.salonplatform.sales.application;

import com.salonplatform.sales.config.SalesMapIngestionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Queues a full Bangalore map sync once on backend start (when enabled). */
@Component
@Order(50)
@RequiredArgsConstructor
@Slf4j
public class SalesMapSyncBootstrapRunner implements ApplicationRunner {

    private final SalesMapIngestionProperties properties;
    private final SalesMapSyncService mapSyncService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || !properties.isBootstrapOnStartup()) {
            return;
        }
        try {
            mapSyncService.failInterruptedJobs();
            var job = mapSyncService.startScheduledAllAreasSync();
            log.info("Bootstrap map sync queued on startup: jobId={}", job.getId());
        } catch (Exception e) {
            log.warn("Bootstrap map sync skipped: {}", e.getMessage());
        }
    }
}
