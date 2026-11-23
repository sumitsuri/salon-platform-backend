package com.salonplatform.forecast;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Trains every active brand's model once a night and shortly after start-up, so the first person to open the forecast
 * does not wait and so how well each model did is always in the log.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ForecastWarmupJob {

    private final SalesForecastService service;
    private final StockReadinessService stock;
    private final JdbcTemplate jdbc;

    @Value("${app.forecast.warmup-on-start:true}")
    private boolean warmupOnStart = true;

    @Scheduled(cron = "${app.forecast.warmup-cron:0 30 4 * * *}", zone = "Asia/Kolkata")
    public void nightly() {
        trainAll();
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (!warmupOnStart) return;
        try {
            Thread.sleep(45_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        trainAll();
    }

    void trainAll() {
        List<UUID> tenants = jdbc.queryForList("""
                SELECT DISTINCT tenant_id FROM invoices
                WHERE deleted_at IS NULL AND issued_at > now() - interval '45 days'
                """, UUID.class);
        log.info("Sales forecast: training {} brand(s)", tenants.size());
        for (UUID tenant : tenants) {
            service.warm(tenant);
            try {
                StockReadinessService.Readiness r = stock.compute(tenant, StockReadinessService.DEFAULT_DAYS, null, java.time.LocalDate.now(SalesForecastService.ZONE));
                for (StockReadinessService.BranchReadiness b : r.branches()) {
                    log.info("  stock {} usageHistory={} short={} low={} ok={} unknown={} bookedServices={}", b.name(), r.hasUsageHistory(), b.shortCount(),
                            b.lowCount(), b.okCount(), b.unknownCount(), b.bookedServices());
                }
            } catch (RuntimeException e) {
                log.warn("Stock readiness check failed for tenant {}: {}", tenant, e.getMessage());
            }
        }
    }
}
