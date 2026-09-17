package com.salonplatform.seed;

import com.salonplatform.domain.entity.ScratchCampaign;
import com.salonplatform.domain.entity.ScratchCampaignPrize;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.enums.DiscountType;
import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.domain.enums.ScratchPrizeKind;
import com.salonplatform.domain.repository.ScratchCampaignRepository;
import com.salonplatform.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Order(50)
@RequiredArgsConstructor
@Slf4j
public class ScratchFootfallDemoSeeder implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final ScratchCampaignRepository campaignRepository;

    @Override
    public void run(ApplicationArguments args) {
        tenantRepository.findBySlug("demo-brand").ifPresent(this::seedIfEmpty);
    }

    private void seedIfEmpty(Tenant tenant) {
        List<ScratchCampaign> existing = campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId());
        if (!existing.isEmpty()) {
            return;
        }
        Instant starts = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant ends = Instant.now().plus(180, ChronoUnit.DAYS);
        ScratchCampaign campaign = ScratchCampaign.builder()
                .tenantId(tenant.getId())
                .name("Visit Surprise Scratch")
                .description("Scratch on every visit for a chance at complimentary add-ons, flat savings, or 20% off premium services.")
                .status(PromoStatus.ACTIVE)
                .startsAt(starts)
                .endsAt(ends)
                .cardsValidDays(14)
                .build();

        campaign.getPrizes().add(ScratchCampaignPrize.builder()
                .campaign(campaign)
                .label("20% off premium service")
                .prizeKind(ScratchPrizeKind.PERCENT_OFF)
                .discountType(DiscountType.PERCENT)
                .discountValue(new BigDecimal("20"))
                .weight(15)
                .sortOrder(0)
                .build());
        campaign.getPrizes().add(ScratchCampaignPrize.builder()
                .campaign(campaign)
                .label("₹150 off today")
                .prizeKind(ScratchPrizeKind.FLAT_OFF)
                .discountType(DiscountType.FLAT)
                .discountValue(new BigDecimal("150"))
                .weight(25)
                .sortOrder(1)
                .build());
        campaign.getPrizes().add(ScratchCampaignPrize.builder()
                .campaign(campaign)
                .label("Complimentary head massage (15 min)")
                .prizeKind(ScratchPrizeKind.COMPLIMENTARY_SERVICE)
                .weight(10)
                .sortOrder(2)
                .build());
        campaign.getPrizes().add(ScratchCampaignPrize.builder()
                .campaign(campaign)
                .label("Better luck next visit")
                .prizeKind(ScratchPrizeKind.TRY_AGAIN)
                .weight(15)
                .sortOrder(3)
                .build());

        campaignRepository.save(campaign);
        log.info("Seeded demo scratch campaign for tenant {}", tenant.getSlug());
    }
}
