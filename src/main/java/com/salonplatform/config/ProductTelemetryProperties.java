package com.salonplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.product-telemetry")
public class ProductTelemetryProperties {

    /** Master switch — when false, ingest is no-op and rollup jobs skip work. */
    private boolean enabled = true;

    private int maxBatchSize = 50;

    private int maxPayloadBytes = 262_144;

    private int queueCapacity = 10_000;

    private long flushIntervalMs = 1_000;

    private int retentionDays = 90;

    /** Cron for hourly rollup (UTC). */
    private String rollupCron = "0 15 * * * *";

    /** Cron for raw event retention purge (UTC). */
    private String retentionCron = "0 30 3 * * *";
}
