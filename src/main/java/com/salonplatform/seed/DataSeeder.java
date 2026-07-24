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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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

        List<SalonService> catalog = ensureServiceCatalog(tenant.getId());
        List<String> addedBranches = new ArrayList<>();
        for (BranchSeed branchSeed : seed.branches()) {
            if (branchRepository.findByTenantIdAndCode(tenant.getId(), branchSeed.code()).isPresent()) {
                continue;
            }
            seedBranch(tenant.getId(), branchSeed, catalog, seed.priceMultiplier());
            addedBranches.add(branchSeed.name());
        }

        if (created) {
            log.info("Seeded tenant '{}' ({}) with {} branches", seed.name(), seed.slug(), seed.branches().size());
            log.info("  Admin: {} / {}", seed.adminEmail(), seed.adminPassword());
        } else if (!addedBranches.isEmpty()) {
            log.info("Seeded additional branches for '{}': {}", seed.name(), String.join(", ", addedBranches));
        }
    }

    private List<SalonService> ensureServiceCatalog(UUID tenantId) {
        List<SalonService> existing = salonServiceRepository.findByTenantIdAndActiveTrue(tenantId);
        if (!existing.isEmpty()) {
            return existing;
        }

        ServiceCategory hair = categoryRepository.save(ServiceCategory.builder()
                .tenantId(tenantId).name("Hair").sortOrder(1).active(true).build());
        ServiceCategory skin = categoryRepository.save(ServiceCategory.builder()
                .tenantId(tenantId).name("Skin").sortOrder(2).active(true).build());
        ServiceCategory grooming = categoryRepository.save(ServiceCategory.builder()
                .tenantId(tenantId).name("Grooming").sortOrder(3).active(true).build());

        SalonService haircut = salonServiceRepository.save(SalonService.builder()
                .tenantId(tenantId).categoryId(hair.getId()).name("Haircut Men")
                .sacCode("9997").gstRate(new BigDecimal("18")).durationMinutes(30).active(true).build());
        SalonService beard = salonServiceRepository.save(SalonService.builder()
                .tenantId(tenantId).categoryId(grooming.getId()).name("Beard Trim")
                .sacCode("9997").gstRate(new BigDecimal("18")).durationMinutes(15).active(true).build());
        SalonService facial = salonServiceRepository.save(SalonService.builder()
                .tenantId(tenantId).categoryId(skin.getId()).name("Facial Classic")
                .sacCode("9997").gstRate(new BigDecimal("18")).durationMinutes(45).active(true).build());
        SalonService color = salonServiceRepository.save(SalonService.builder()
                .tenantId(tenantId).categoryId(hair.getId()).name("Hair Color")
                .sacCode("9997").gstRate(new BigDecimal("18")).durationMinutes(90).active(true).build());

        return List.of(haircut, beard, facial, color);
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

        if (branchServiceRepository.findByTenantIdAndBranchId(tenantId, branch.getId()).isEmpty()) {
            BigDecimal branchExtra = "WEB".equals(seed.code()) ? new BigDecimal("1.1") : BigDecimal.ONE;
            BigDecimal multiplier = priceMultiplier.multiply(branchExtra);
            for (SalonService service : catalog) {
                BigDecimal base = switch (service.getName()) {
                    case "Haircut Men" -> new BigDecimal("300");
                    case "Beard Trim" -> new BigDecimal("150");
                    case "Facial Classic" -> new BigDecimal("1200");
                    case "Hair Color" -> new BigDecimal("2500");
                    default -> new BigDecimal("500");
                };
                BigDecimal price = "Facial Classic".equals(service.getName())
                        ? base
                        : base.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
                branchServiceRepository.save(BranchService.builder()
                        .tenantId(tenantId)
                        .branchId(branch.getId())
                        .serviceId(service.getId())
                        .price(price)
                        .active(true)
                        .build());
            }
        }

        return branch;
    }
}
