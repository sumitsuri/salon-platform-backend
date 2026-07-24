package com.salonplatform.seed;

import com.salonplatform.domain.entity.LocalCompetitor;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.enums.CompetitorType;
import com.salonplatform.domain.repository.LocalCompetitorRepository;
import com.salonplatform.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Seeds demo local competitors for Market Pulse Phase 3. */
@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class LocalCompetitorSeeder implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final LocalCompetitorRepository localCompetitorRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Tenant demo = tenantRepository.findBySlug("demo-brand").orElse(null);
        if (demo == null) return;
        if (!localCompetitorRepository.findByTenantIdAndActiveTrueOrderByNameAsc(demo.getId()).isEmpty()) {
            return;
        }
        seed(demo.getId(), "Glow Studio Manyata", CompetitorType.LOCAL,
                new BigDecimal("19500"), new BigDecimal("1380"), new BigDecimal("16"),
                new BigDecimal("24"), new BigDecimal("52"), "Manyata Tech Park gate");
        seed(demo.getId(), "Urban Cuts ORR", CompetitorType.LOCAL,
                new BigDecimal("16200"), new BigDecimal("1050"), new BigDecimal("9"),
                new BigDecimal("21"), new BigDecimal("44"), "Outer Ring Rd");
        seed(demo.getId(), "Naturals Express", CompetitorType.ASPIRATIONAL,
                new BigDecimal("22000"), new BigDecimal("1520"), new BigDecimal("19"),
                new BigDecimal("28"), new BigDecimal("58"), "Aspirational chain benchmark");
        log.info("Seeded local competitors for demo-brand Market Pulse");
    }

    private void seed(UUID tenantId, String name, CompetitorType type,
                      BigDecimal rev, BigDecimal atv, BigDecimal retail,
                      BigDecimal margin, BigDecimal repeat, String address) {
        localCompetitorRepository.save(LocalCompetitor.builder()
                .tenantId(tenantId)
                .name(name)
                .competitorType(type)
                .revenuePerBranchDay(rev)
                .avgTicket(atv)
                .retailAttachPercent(retail)
                .netMarginPercent(margin)
                .repeatVisitRate(repeat)
                .address(address)
                .active(true)
                .build());
    }
}
