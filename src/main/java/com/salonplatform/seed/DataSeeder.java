package com.salonplatform.seed;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.SalonTier;
import com.salonplatform.domain.enums.TenantStatus;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.enums.StaffRole;
import com.salonplatform.domain.repository.*;
import com.salonplatform.seed.SeedCatalog.BranchSeed;
import com.salonplatform.seed.SeedCatalog.StaffSeed;
import com.salonplatform.seed.SeedCatalog.TenantSeed;
import com.salonplatform.service.ProductionTenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final BranchServiceRepository branchServiceRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateCardCatalogSync rateCardCatalogSync;
    private final ProductionTenantGuard productionTenantGuard;
    private final Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        ensurePlatformAdmin();

        for (TenantSeed seed : SeedCatalog.TENANTS) {
            ensureTenant(seed);
        }
    }

    private void ensurePlatformAdmin() {
        if (userRepository.findByEmail("platform@salonplatform.local").isEmpty()) {
            userRepository.save(User.builder()
                    .name("Platform Admin")
                    .email("platform@salonplatform.local")
                    .password(passwordEncoder.encode("admin123"))
                    .role(UserRole.PLATFORM_SUPER_ADMIN)
                    .active(true)
                    .build());
            log.info("Seeded platform admin: platform@salonplatform.local / admin123");
        }
    }

    private void ensureTenant(TenantSeed seed) {
        if (productionTenantGuard.shouldSkipSystemMutation(seed.slug())) {
            log.info("Skipping seed mutations for protected production tenant {}", seed.slug());
            return;
        }
        Tenant tenant = tenantRepository.findBySlug(seed.slug()).orElse(null);
        boolean created = false;
        if (tenant == null) {
            tenant = tenantRepository.save(Tenant.builder()
                    .name(seed.name())
                    .slug(seed.slug())
                    .primaryColor(seed.primaryColor())
                    .status(TenantStatus.ACTIVE)
                    .benchmarkOptIn(true)
                    .marketCity("Bangalore")
                    .salonTier(seed.salonTier())
                    .build());
            created = true;
        } else {
            tenant.setBenchmarkOptIn(true);
            tenant.setMarketCity("Bangalore");
            tenant.setSalonTier(seed.salonTier());
            tenantRepository.save(tenant);
        }

        if (userRepository.findByEmail(seed.adminEmail()).isEmpty()) {
            userRepository.save(User.builder()
                    .tenantId(tenant.getId())
                    .name(seed.adminName())
                    .email(seed.adminEmail())
                    .password(passwordEncoder.encode(seed.adminPassword()))
                    .role(UserRole.BRAND_ADMIN)
                    .active(true)
                    .build());
        }

        if ("demo-brand".equals(seed.slug())
                || "velvet-scissors".equals(seed.slug())
                || "mystic-wellness".equals(seed.slug())) {
            // Catalog for these tenants is managed by startup patches (MysticWellness / VelvetScissors).
            if (created) {
                log.info("Seeded tenant '{}' ({}) — catalog applied by startup patch", seed.name(), seed.slug());
                log.info("  Admin: {} / {}", seed.adminEmail(), seed.adminPassword());
            }
            if ("demo-brand".equals(seed.slug()) && !isProdProfile()) {
                enableDemoOnlineBooking(tenant);
            }
            for (BranchSeed branchSeed : seed.branches()) {
                if (branchRepository.findByTenantIdAndCode(tenant.getId(), branchSeed.code()).isPresent()) {
                    continue;
                }
                seedBranch(tenant.getId(), branchSeed, List.of(), seed.priceMultiplier());
                log.info("Seeded additional branch for '{}': {}", seed.name(), branchSeed.name());
            }
            return;
        }

        List<SalonService> catalog = rateCardCatalogSync.syncTenant(tenant.getId(), seed.slug(), seed.priceMultiplier());
        List<String> addedBranches = new ArrayList<>();
        for (BranchSeed branchSeed : seed.branches()) {
            if (branchRepository.findByTenantIdAndCode(tenant.getId(), branchSeed.code()).isPresent()) {
                continue;
            }
            seedBranch(tenant.getId(), branchSeed, catalog, seed.priceMultiplier());
            addedBranches.add(branchSeed.name());
        }
        // Ensure pricing for all existing branches after catalog upgrades.
        rateCardCatalogSync.syncTenant(tenant.getId(), seed.slug(), seed.priceMultiplier());

        if (created) {
            log.info("Seeded tenant '{}' ({}) with {} branches", seed.name(), seed.slug(), seed.branches().size());
            log.info("  Admin: {} / {}", seed.adminEmail(), seed.adminPassword());
        } else if (!addedBranches.isEmpty()) {
            log.info("Seeded additional branches for '{}': {}", seed.name(), String.join(", ", addedBranches));
        }
    }

    private Branch seedBranch(UUID tenantId, BranchSeed seed, List<SalonService> catalog,
                              BigDecimal priceMultiplier) {
        Branch branch = branchRepository.save(Branch.builder()
                .tenantId(tenantId)
                .name(seed.name())
                .code(seed.code())
                .address(seed.address())
                .societyDefault(seed.societyDefault())
                .gstin(seed.gstin())
                .phone(seed.phone())
                .openTime("09:00")
                .closeTime("21:00")
                .latitude(seed.latitude())
                .longitude(seed.longitude())
                .geofenceRadiusMeters(150)
                .attendanceGraceMinutes(15)
                .monthlySalesTarget(new BigDecimal(seed.monthlySalesTarget()))
                .status(BranchStatus.ACTIVE)
                .build());

        if (userRepository.findByEmail(seed.managerEmail()).isEmpty()) {
            userRepository.save(User.builder()
                    .tenantId(tenantId)
                    .branchId(branch.getId())
                    .name(seed.managerName())
                    .email(seed.managerEmail())
                    .password(passwordEncoder.encode("manager123"))
                    .role(UserRole.SALON_MANAGER)
                    .active(true)
                    .build());
        }

        for (StaffSeed staff : seed.staff()) {
            if (staffRepository.findByTenantIdAndBiometricId(tenantId, staff.biometricId()).isPresent()) {
                continue;
            }
            Staff.StaffBuilder builder = Staff.builder()
                    .tenantId(tenantId)
                    .branchId(branch.getId())
                    .name(staff.name())
                    .role(StaffRole.STYLIST)
                    .skills(staff.skills())
                    .biometricId(staff.biometricId())
                    .salary(new BigDecimal(staff.salary()))
                    .joiningDate(staff.joiningDate())
                    .idProofCollected(staff.idProofCollected())
                    .monthlySalesTarget(new BigDecimal(staff.monthlySalesTarget()))
                    .incentivePercent(new BigDecimal(staff.incentivePercent()))
                    .active(true);
            if (staff.idProofReference() != null) {
                builder.idProofReference(staff.idProofReference());
            }
            staffRepository.save(builder.build());
        }

        // Branch service pricing is applied by RateCardCatalogSync after all branches exist.
        return branch;
    }

    private boolean isProdProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private void enableDemoOnlineBooking(Tenant tenant) {
        if (!Boolean.TRUE.equals(tenant.getOnlineBookingEnabled())) {
            tenant.setOnlineBookingEnabled(true);
            tenantRepository.save(tenant);
        }
        branchRepository.findByTenantId(tenant.getId()).forEach(branch -> {
            if (!Boolean.TRUE.equals(branch.getOnlineBookingEnabled())) {
                branch.setOnlineBookingEnabled(true);
                branchRepository.save(branch);
            }
        });
    }
}
