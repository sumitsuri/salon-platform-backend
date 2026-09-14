package com.salonplatform.seed;

import com.salonplatform.domain.entity.SalonService;
import com.salonplatform.domain.entity.ServicePackagePlan;
import com.salonplatform.domain.entity.ServicePackagePlanItem;
import com.salonplatform.domain.enums.PackageRedemptionMode;
import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.domain.repository.SalonServiceRepository;
import com.salonplatform.domain.repository.ServicePackagePlanItemRepository;
import com.salonplatform.domain.repository.ServicePackagePlanRepository;
import com.salonplatform.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds up to 20 predefined service packages per tenant from the catalog (for quick sell on floor).
 */
@Component
@Order(7)
@RequiredArgsConstructor
@Slf4j
public class ServicePackagePredefinedSeeder implements ApplicationRunner {

    private static final int TEMPLATE_COUNT = 20;
    private static final String[] NAME_PREFIXES = {
            "Radiance", "Revive", "Essentials", "Signature", "Weekend", "Glow", "Classic", "Premium",
            "Express", "Complete", "Deluxe", "Refresh", "Total Care", "Smart", "Value", "Seasonal",
            "Couples", "Monthly", "Quick", "Ultimate"
    };

    private final TenantRepository tenantRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final ServicePackagePlanRepository planRepository;
    private final ServicePackagePlanItemRepository planItemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (var tenant : tenantRepository.findAll()) {
            seedTenant(tenant.getId());
        }
    }

    private void seedTenant(UUID tenantId) {
        if (planRepository.findByTenantIdAndPredefinedRank(tenantId, 1).isPresent()) {
            return;
        }
        List<SalonService> services = salonServiceRepository.findByTenantIdAndActiveTrue(tenantId).stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
        if (services.size() < 2) {
            return;
        }

        log.info("Seeding predefined service packages for tenant {}", tenantId);
        for (int rank = 1; rank <= TEMPLATE_COUNT; rank++) {
            int offset = (rank - 1) % Math.max(1, services.size() - 1);
            List<SalonService> picked = new ArrayList<>();
            picked.add(services.get(offset % services.size()));
            picked.add(services.get((offset + 1) % services.size()));
            if (rank % 3 == 0 && services.size() > 2) {
                picked.add(services.get((offset + 2) % services.size()));
            }

            BigDecimal listTotal = BigDecimal.ZERO;
            for (SalonService svc : picked) {
                BigDecimal price = svc.getListPrice() != null ? svc.getListPrice() : BigDecimal.valueOf(500);
                listTotal = listTotal.add(price);
            }
            BigDecimal packagePrice = listTotal.multiply(BigDecimal.valueOf(0.85))
                    .setScale(2, RoundingMode.HALF_UP);
            PackageRedemptionMode mode = rank % 5 == 0
                    ? PackageRedemptionMode.SINGLE_VISIT : PackageRedemptionMode.MULTI_VISIT;
            int validity = rank % 4 == 0 ? 30 : (rank % 3 == 0 ? 180 : 90);

            ServicePackagePlan plan = planRepository.save(ServicePackagePlan.builder()
                    .tenantId(tenantId)
                    .name(NAME_PREFIXES[rank - 1] + " Package " + rank)
                    .description("Predefined combo — " + picked.size() + " services at a bundle price")
                    .listPriceTotal(listTotal.setScale(2, RoundingMode.HALF_UP))
                    .packagePrice(packagePrice)
                    .validityDays(validity)
                    .redemptionMode(mode)
                    .branchIds(null)
                    .status(PromoStatus.ACTIVE)
                    .predefinedRank(rank)
                    .build());

            int sort = 0;
            for (SalonService svc : picked) {
                planItemRepository.save(ServicePackagePlanItem.builder()
                        .planId(plan.getId())
                        .serviceId(svc.getId())
                        .quantity(1)
                        .sortOrder(sort++)
                        .build());
            }
        }
    }
}
