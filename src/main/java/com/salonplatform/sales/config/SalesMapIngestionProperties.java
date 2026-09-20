package com.salonplatform.sales.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.sales.map-ingestion")
public class SalesMapIngestionProperties {

    private boolean enabled = true;

    /** Cron for scheduled Bangalore map ingestion (default: every 6 hours). */
    private String cron = "0 0 0/6 * * *";

    /** Fixed radius for background ingestion job. */
    private int radiusKm = 5;

    /** Exclusive claim duration for sales reps. */
    private int claimDurationDays = 30;

    /** Queue full-area sync when the API server starts. */
    private boolean bootstrapOnStartup = true;
}
