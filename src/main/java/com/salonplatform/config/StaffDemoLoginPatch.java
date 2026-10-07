package com.salonplatform.config;

import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.service.StaffAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ensures the demo staff app login exists on databases seeded before SALON_STAFF was introduced.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class StaffDemoLoginPatch implements ApplicationRunner {

    private static final String DEMO_BIOMETRIC = "FP-AMIT-LITHOS";

    private final StaffRepository staffRepository;
    private final StaffAccountService staffAccountService;

    @Override
    public void run(ApplicationArguments args) {
        staffRepository.findAll().stream()
                .filter(s -> DEMO_BIOMETRIC.equals(s.getBiometricId()))
                .filter(s -> s.getUserId() == null)
                .findFirst()
                .ifPresent(this::ensureAmitLogin);
    }

    private void ensureAmitLogin(Staff staff) {
        try {
            staffAccountService.ensureDemoLogin(
                    staff,
                    "amit.lithos@demo-brand.local",
                    "staff123",
                    "Gents Hair Dresser");
            log.info("Demo staff login ensured for {}", staff.getName());
        } catch (Exception e) {
            log.warn("Demo staff login patch skipped: {}", e.getMessage());
        }
    }
}
