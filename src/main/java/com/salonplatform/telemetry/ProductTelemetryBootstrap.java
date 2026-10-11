package com.salonplatform.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@RequiredArgsConstructor
public class ProductTelemetryBootstrap implements ApplicationRunner {

    private final ProductTelemetryIngestService ingestService;
    private final ProductUsageRollupScheduler rollupScheduler;

    @Override
    public void run(ApplicationArguments args) {
        ingestService.startFlusher();
        rollupScheduler.bootstrapRollup();
    }
}
