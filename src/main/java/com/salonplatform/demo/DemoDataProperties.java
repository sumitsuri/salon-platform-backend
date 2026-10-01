package com.salonplatform.demo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Demo showcase brand generated on demand by a platform admin (never at deploy time).
 * Login passwords come from the environment; a rebuild refuses to run without them.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.demo-data")
public class DemoDataProperties {

    /** Master switch — the rebuild endpoint and the top-up schedule are inert unless true. */
    private boolean enabled = false;

    private String brandName = "Aura Salon & Spa";
    private String slug = "aura-demo";
    private String primaryColor = "#b45309";
    /** Login emails are {@code <local>@<emailDomain>}; use a domain we own so resets never leak. */
    private String emailDomain = "antrahq.com";

    private String ownerPassword;
    private String managerPassword;

    /** Days of history generated before today. */
    private int historyDays = 365;
    /** Average completed bills per branch per day before seasonality and story arcs. */
    private int billsPerBranchPerDay = 20;
    /** Fixed seed so a rebuild on the same date reproduces the same people and numbers. */
    private long randomSeed = 20251001L;

    /** Rows per JDBC batch, and the pause between batches, so the job never crowds live traffic. */
    private int batchSize = 500;
    private long pauseMillisBetweenBatches = 40;

    /** Keeps "today" alive for live demos: new bills, attendance and appointments as the day progresses. */
    private boolean topUpEnabled = false;
}
