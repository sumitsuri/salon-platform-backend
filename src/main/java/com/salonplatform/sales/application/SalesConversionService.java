package com.salonplatform.sales.application;

import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.sales.domain.entity.SalesLead;
import com.salonplatform.sales.domain.enums.IncentiveEventType;
import com.salonplatform.sales.domain.enums.LeadStage;
import com.salonplatform.sales.domain.port.ProvisionMode;
import com.salonplatform.sales.domain.port.TenantProvisionResult;
import com.salonplatform.sales.infrastructure.MonolithTenantProvisioningAdapter;
import com.salonplatform.sales.domain.repository.SalesLeadRepository;
import com.salonplatform.sales.domain.repository.SalesStageHistoryRepository;
import com.salonplatform.sales.domain.entity.SalesStageHistory;
import com.salonplatform.sales.application.SalesPricingUtils;
import com.salonplatform.sales.dto.ConvertSalesLeadRequest;
import com.salonplatform.sales.dto.SalesLeadResponse;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesConversionService {

    private static final EnumSet<LeadStage> CONVERTIBLE = EnumSet.of(
            LeadStage.INTERESTED, LeadStage.FREE_TRIAL, LeadStage.PITCHED
    );

    private final SalesLeadRepository leadRepository;
    private final SalesStageHistoryRepository stageHistoryRepository;
    private final MonolithTenantProvisioningAdapter tenantProvisioningAdapter;
    private final SalesIncentiveService incentiveService;
    private final SalesLeadService salesLeadService;

    @Transactional
    public SalesLeadResponse convert(UUID leadId, ConvertSalesLeadRequest request) {
        SecurityUtils.assertPlatformAdmin();
        SalesLead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        if (lead.getStage() == LeadStage.WON) {
            throw new BadRequestException("Lead is already converted");
        }
        if (lead.getStage() == LeadStage.LOST) {
            throw new BadRequestException("Cannot convert a lost lead");
        }
        if (!CONVERTIBLE.contains(lead.getStage())) {
            throw new BadRequestException("Lead must be at least PITCHED to convert");
        }

        LeadStage from = lead.getStage();
        TenantProvisionResult result = tenantProvisioningAdapter.provisionFromLeadWithRequest(
                lead, request, ProvisionMode.FULL);

        lead.setStage(LeadStage.WON);
        lead.setConvertedTenantId(result.tenantId());
        lead.setConvertedAt(Instant.now());
        if (request.getProjectedMrr() != null) {
            lead.setProjectedMrr(request.getProjectedMrr());
        } else if (lead.getQuotedAmount() != null || lead.getFinalPaidAmount() != null) {
            lead.setProjectedMrr(SalesPricingUtils.monthlyEquivalent(
                    SalesPricingUtils.effectiveRevenueAmount(lead), lead.getBillingPeriod()));
        }
        lead.setPlanTier(request.getPlanTier() != null ? request.getPlanTier() : lead.getPlanTier());

        SalesLead saved = leadRepository.save(lead);

        stageHistoryRepository.save(SalesStageHistory.builder()
                .leadId(leadId)
                .fromStage(from)
                .toStage(LeadStage.WON)
                .changedByUserId(SecurityUtils.currentUserId())
                .notes("Converted to tenant " + result.tenantSlug())
                .build());

        incentiveService.recordIncentiveIfEligible(saved, IncentiveEventType.WON);
        return salesLeadService.toResponse(saved);
    }
}
