package com.salonplatform.sales.application;

import com.salonplatform.sales.config.SalesMapIngestionProperties;
import com.salonplatform.sales.dto.MapIngestionSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SalesMapIngestionScheduler {

    private final SalesMapIngestionProperties properties;
    private final SalesMapSyncService mapSyncService;

    @Scheduled(cron = "${app.sales.map-ingestion.cron:0 0 0/6 * * *}")
    public void ingestBangaloreMapLeads() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            var job = mapSyncService.startScheduledAllAreasSync();
            log.info("Scheduled map sync queued: jobId={} areas={}", job.getId(), job.getTotalAreas());
        } catch (Exception e) {
            log.error("Map ingestion schedule failed: {}", e.getMessage(), e);
        }
    }
}
