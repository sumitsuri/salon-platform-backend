package com.salonplatform.seed;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.*;
import com.salonplatform.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

        if (tenantRepository.findBySlug("demo-brand").isPresent()) {
            return;
        }

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Demo Salon Brand")
                .slug("demo-brand")
                .primaryColor("#7c3aed")
                .status(TenantStatus.ACTIVE)
                .build());

        Branch lithos = branchRepository.save(Branch.builder()
                .tenantId(tenant.getId())
                .name("Mantri Lithos")
                .code("LIT")
                .address("Mantri Lithos, Bangalore")
                .societyDefault("Mantri Lithos")
                .gstin("29AABCU9603R1ZM")
                .phone("9876543210")
                .openTime("09:00")
                .closeTime("21:00")
                .monthlySalesTarget(new BigDecimal("400000"))
                .status(BranchStatus.ACTIVE)
                .build());

        Branch webcity = branchRepository.save(Branch.builder()
                .tenantId(tenant.getId())
                .name("Mantri Webcity")
                .code("WEB")
                .address("Mantri Webcity, Bangalore")
                .societyDefault("Mantri Webcity")
                .gstin("29AABCU9603R1ZN")
                .phone("9876543211")
                .openTime("09:00")
                .closeTime("21:00")
                .monthlySalesTarget(new BigDecimal("350000"))
                .status(BranchStatus.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .name("Brand CEO")
                .email("ceo@demo-brand.local")
                .password(passwordEncoder.encode("ceo123"))
                .role(UserRole.BRAND_ADMIN)
                .active(true)
                .build());

        userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .branchId(lithos.getId())
                .name("Lithos Manager")
                .email("manager.lithos@demo-brand.local")
                .password(passwordEncoder.encode("manager123"))
                .role(UserRole.SALON_MANAGER)
                .active(true)
                .build());

        userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .branchId(webcity.getId())
                .name("Webcity Manager")
                .email("manager.webcity@demo-brand.local")
                .password(passwordEncoder.encode("manager123"))
                .role(UserRole.SALON_MANAGER)
                .active(true)
                .build());

        Staff amitL = staffRepository.save(Staff.builder().tenantId(tenant.getId()).branchId(lithos.getId())
                .name("Amit").role(StaffRole.STYLIST).skills("Hair,Grooming").biometricId("FP-AMIT-LITHOS")
                .salary(new BigDecimal("25000")).joiningDate(LocalDate.of(2024, 3, 1)).idProofCollected(true)
                .idProofReference("Aadhaar XXXX4521").monthlySalesTarget(new BigDecimal("120000"))
                .incentivePercent(new BigDecimal("5")).active(true).build());
        Staff priyaL = staffRepository.save(Staff.builder().tenantId(tenant.getId()).branchId(lithos.getId())
                .name("Priya").role(StaffRole.STYLIST).skills("Skin,Hair").biometricId("FP-PRIYA-LITHOS")
                .salary(new BigDecimal("28000")).joiningDate(LocalDate.of(2023, 8, 15)).idProofCollected(true)
                .idProofReference("PAN XXXX7890").monthlySalesTarget(new BigDecimal("150000"))
                .incentivePercent(new BigDecimal("5")).active(true).build());
        Staff amitW = staffRepository.save(Staff.builder().tenantId(tenant.getId()).branchId(webcity.getId())
                .name("Ravi").role(StaffRole.STYLIST).skills("Hair,Grooming").biometricId("FP-RAVI-WEBCITY")
                .salary(new BigDecimal("22000")).joiningDate(LocalDate.of(2024, 6, 1)).idProofCollected(true)
                .idProofReference("Aadhaar XXXX3312").monthlySalesTarget(new BigDecimal("100000"))
                .incentivePercent(new BigDecimal("4")).active(true).build());
        Staff priyaW = staffRepository.save(Staff.builder().tenantId(tenant.getId()).branchId(webcity.getId())
                .name("Sneha").role(StaffRole.STYLIST).skills("Skin,Nails").biometricId("FP-SNEHA-WEBCITY")
                .salary(new BigDecimal("24000")).joiningDate(LocalDate.of(2024, 1, 10)).idProofCollected(false)
                .monthlySalesTarget(new BigDecimal("110000")).incentivePercent(new BigDecimal("4")).active(true).build());

        ServiceCategory hair = categoryRepository.save(ServiceCategory.builder()
                .tenantId(tenant.getId()).name("Hair").sortOrder(1).active(true).build());
        ServiceCategory skin = categoryRepository.save(ServiceCategory.builder()
                .tenantId(tenant.getId()).name("Skin").sortOrder(2).active(true).build());
        ServiceCategory grooming = categoryRepository.save(ServiceCategory.builder()
                .tenantId(tenant.getId()).name("Grooming").sortOrder(3).active(true).build());

        SalonService haircut = salonServiceRepository.save(SalonService.builder()
                .tenantId(tenant.getId()).categoryId(hair.getId()).name("Haircut Men")
                .sacCode("9997").gstRate(new BigDecimal("18")).durationMinutes(30).active(true).build());
        SalonService beard = salonServiceRepository.save(SalonService.builder()
                .tenantId(tenant.getId()).categoryId(grooming.getId()).name("Beard Trim")
                .sacCode("9997").gstRate(new BigDecimal("18")).durationMinutes(15).active(true).build());
        SalonService facial = salonServiceRepository.save(SalonService.builder()
                .tenantId(tenant.getId()).categoryId(skin.getId()).name("Facial Classic")
                .sacCode("9997").gstRate(new BigDecimal("18")).durationMinutes(45).active(true).build());
        SalonService color = salonServiceRepository.save(SalonService.builder()
                .tenantId(tenant.getId()).categoryId(hair.getId()).name("Hair Color")
                .sacCode("9997").gstRate(new BigDecimal("18")).durationMinutes(90).active(true).build());

        for (Branch branch : List.of(lithos, webcity)) {
            BigDecimal multiplier = branch.getCode().equals("WEB") ? new BigDecimal("1.1") : BigDecimal.ONE;
            branchServiceRepository.save(BranchService.builder().tenantId(tenant.getId()).branchId(branch.getId())
                    .serviceId(haircut.getId()).price(new BigDecimal("300").multiply(multiplier)).active(true).build());
            branchServiceRepository.save(BranchService.builder().tenantId(tenant.getId()).branchId(branch.getId())
                    .serviceId(beard.getId()).price(new BigDecimal("150").multiply(multiplier)).active(true).build());
            branchServiceRepository.save(BranchService.builder().tenantId(tenant.getId()).branchId(branch.getId())
                    .serviceId(facial.getId()).price(new BigDecimal("1200")).active(true).build());
            branchServiceRepository.save(BranchService.builder().tenantId(tenant.getId()).branchId(branch.getId())
                    .serviceId(color.getId()).price(new BigDecimal("2500").multiply(multiplier)).active(true).build());
        }

        log.info("Seeded demo tenant with Lithos + Webcity pilot branches");
        log.info("Brand Admin: ceo@demo-brand.local / ceo123");
        log.info("Lithos Manager: manager.lithos@demo-brand.local / manager123");
        log.info("Webcity Manager: manager.webcity@demo-brand.local / manager123");
    }
}
