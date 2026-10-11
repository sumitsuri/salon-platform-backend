package com.salonplatform.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(25)
@RequiredArgsConstructor
public class ProductUsageBootstrapRollup implements ApplicationRunner {

    private final ProductUsageRollupScheduler rollupScheduler;

    @Override
    public void run(ApplicationArguments args) {
        rollupScheduler.bootstrapRollup();
    }
}
