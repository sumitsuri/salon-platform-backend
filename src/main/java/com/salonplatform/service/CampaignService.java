package com.salonplatform.service;

import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.MarketingCampaign;
import com.salonplatform.domain.enums.CampaignStatus;
import com.salonplatform.domain.enums.MessageChannel;
import com.salonplatform.domain.entity.MessageDeliveryLog;
import com.salonplatform.domain.repository.CustomerRepository;
import com.salonplatform.domain.repository.MarketingCampaignRepository;
import com.salonplatform.domain.repository.MessageDeliveryLogRepository;
import com.salonplatform.dto.campaign.CampaignDeliveryResponse;
import com.salonplatform.dto.campaign.CampaignListFilter;
import com.salonplatform.dto.campaign.CampaignPreviewResponse;
import com.salonplatform.dto.campaign.CampaignResponse;
import com.salonplatform.dto.campaign.CreateCampaignRequest;
import com.salonplatform.dto.customer.CustomerResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.repository.CustomerSpecifications;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import com.salonplatform.util.CampaignFilterValues;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private static final int PREVIEW_CUSTOMER_LIMIT = 100;

    private final MarketingCampaignRepository campaignRepository;
    private final CustomerRepository customerRepository;
    private final MessageDeliveryLogRepository deliveryLogRepository;
    private final CustomerService customerService;
    private final CampaignDispatchService campaignDispatchService;

    @Transactional
    public CampaignResponse create(CreateCampaignRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UserPrincipal user = SecurityUtils.currentUser();
        UUID tenantId = SecurityUtils.requireTenantId();

        MarketingCampaign campaign = MarketingCampaign.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .channel(request.getChannel())
                .messageText(request.getMessageText())
                .filterName(CampaignFilterValues.serialize(
                        CampaignFilterValues.resolveNames(request.getFilterName(), request.getFilterNames())))
                .filterSociety(request.getFilterSociety())
                .filterPhone(CampaignFilterValues.serialize(
                        CampaignFilterValues.resolveNames(request.getFilterPhone(), request.getFilterPhones())))
                .filterMinVisitCount(request.getFilterMinVisitCount())
                .filterMaxVisitCount(request.getFilterMaxVisitCount())
                .filterMinLifetimeSpend(request.getFilterMinLifetimeSpend())
                .filterMaxLifetimeSpend(request.getFilterMaxLifetimeSpend())
                .filterLastVisitFrom(request.getFilterLastVisitFrom())
                .filterLastVisitTo(request.getFilterLastVisitTo())
                .filterWhatsappOptInOnly(request.getFilterWhatsappOptInOnly() != null
                        ? request.getFilterWhatsappOptInOnly() : true)
                .filterSmsOptInOnly(request.getFilterSmsOptInOnly() != null
                        ? request.getFilterSmsOptInOnly() : true)
                .createdByUserId(user.getId())
                .build();

        long count = countMatching(campaign);
        campaign.setRecipientCount((int) Math.min(count, Integer.MAX_VALUE));
        return toResponse(campaignRepository.save(campaign));
    }

    public List<CampaignResponse> list(CampaignListFilter filter) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        CampaignListFilter effective = filter != null ? filter : CampaignListFilter.builder().build();
        return campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(c -> matchesListFilter(c, effective))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<CampaignDeliveryResponse> listDeliveries(UUID campaignId) {
        SecurityUtils.assertBrandAdminOrAbove();
        MarketingCampaign campaign = loadCampaign(campaignId);
        List<MessageDeliveryLog> logs = deliveryLogRepository
                .findByTenantIdAndCampaignIdOrderByCreatedAtDesc(campaign.getTenantId(), campaignId);
        if (logs.isEmpty()) {
            return List.of();
        }
        List<UUID> customerIds = logs.stream()
                .map(MessageDeliveryLog::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Customer> customersById = customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, c -> c));
        return logs.stream()
                .map(log -> toDeliveryResponse(log, customersById.get(log.getCustomerId())))
                .toList();
    }

    private CampaignDeliveryResponse toDeliveryResponse(MessageDeliveryLog log, Customer customer) {
        return CampaignDeliveryResponse.builder()
                .id(log.getId())
                .customerId(log.getCustomerId())
                .customerName(customer != null ? customer.getName() : null)
                .recipientPhone(log.getRecipientPhone())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .providerMessageId(log.getProviderMessageId())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private boolean matchesListFilter(MarketingCampaign campaign, CampaignListFilter filter) {
        if (filter.getName() != null && !filter.getName().isBlank()) {
            String q = filter.getName().trim().toLowerCase();
            if (campaign.getName() == null || !campaign.getName().toLowerCase().contains(q)) {
                return false;
            }
        }
        if (filter.getChannel() != null && campaign.getChannel() != filter.getChannel()) {
            return false;
        }
        if (filter.getStatus() != null && campaign.getStatus() != filter.getStatus()) {
            return false;
        }
        return true;
    }

    public List<CampaignResponse> list() {
        return list(CampaignListFilter.builder().build());
    }

    public CampaignResponse get(UUID id) {
        SecurityUtils.assertBrandAdminOrAbove();
        return toResponse(loadCampaign(id));
    }

    public CampaignPreviewResponse preview(CreateCampaignRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Specification<Customer> spec = buildSpecFromRequest(tenantId, request);
        long count = customerRepository.count(spec);
        List<CustomerResponse> customers = customerService.listForCampaignPreview(spec, PREVIEW_CUSTOMER_LIMIT);
        return CampaignPreviewResponse.builder()
                .matchingCustomers(count)
                .customers(customers)
                .previewTruncated(count > customers.size())
                .build();
    }

    @Transactional
    public CampaignResponse send(UUID id) {
        SecurityUtils.assertBrandAdminOrAbove();
        MarketingCampaign campaign = loadCampaign(id);

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new BadRequestException("error.campaign.alreadySent");
        }

        List<Customer> recipients = customerRepository.findAll(buildSpec(campaign));
        campaign.setRecipientCount(recipients.size());
        campaign.setStatus(CampaignStatus.SENDING);
        campaignRepository.save(campaign);

        campaignDispatchService.dispatch(campaign.getId(), recipients);
        return toResponse(campaign);
    }

    private long countMatching(MarketingCampaign campaign) {
        return customerRepository.count(buildSpec(campaign));
    }

    private Specification<Customer> buildSpec(MarketingCampaign campaign) {
        return CustomerSpecifications.fromCampaignFilters(
                campaign.getTenantId(),
                CampaignFilterValues.deserialize(campaign.getFilterName()),
                CampaignFilterValues.deserialize(campaign.getFilterPhone()),
                campaign.getFilterSociety(),
                campaign.getFilterMinVisitCount(),
                campaign.getFilterMaxVisitCount(),
                campaign.getFilterMinLifetimeSpend(),
                campaign.getFilterMaxLifetimeSpend(),
                campaign.getFilterLastVisitFrom(),
                campaign.getFilterLastVisitTo(),
                campaign.getChannel() == MessageChannel.WHATSAPP
                        ? campaign.getFilterWhatsappOptInOnly() : false,
                campaign.getChannel() == MessageChannel.SMS
                        ? campaign.getFilterSmsOptInOnly() : false);
    }

    private Specification<Customer> buildSpecFromRequest(UUID tenantId, CreateCampaignRequest request) {
        return CustomerSpecifications.fromCampaignFilters(
                tenantId,
                CampaignFilterValues.resolveNames(request.getFilterName(), request.getFilterNames()),
                CampaignFilterValues.resolveNames(request.getFilterPhone(), request.getFilterPhones()),
                request.getFilterSociety(),
                request.getFilterMinVisitCount(),
                request.getFilterMaxVisitCount(),
                request.getFilterMinLifetimeSpend(),
                request.getFilterMaxLifetimeSpend(),
                request.getFilterLastVisitFrom(),
                request.getFilterLastVisitTo(),
                request.getChannel() == MessageChannel.WHATSAPP
                        ? (request.getFilterWhatsappOptInOnly() != null ? request.getFilterWhatsappOptInOnly() : true)
                        : false,
                request.getChannel() == MessageChannel.SMS
                        ? (request.getFilterSmsOptInOnly() != null ? request.getFilterSmsOptInOnly() : true)
                        : false);
    }

    private MarketingCampaign loadCampaign(UUID id) {
        UUID tenantId = SecurityUtils.requireTenantId();
        MarketingCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.campaign.notFound"));
        if (!campaign.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("error.campaign.notFound");
        }
        return campaign;
    }

    private CampaignResponse toResponse(MarketingCampaign c) {
        return CampaignResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .channel(c.getChannel())
                .status(c.getStatus())
                .messageText(c.getMessageText())
                .filterName(c.getFilterName())
                .filterSociety(c.getFilterSociety())
                .filterPhone(c.getFilterPhone())
                .filterMinVisitCount(c.getFilterMinVisitCount())
                .filterMaxVisitCount(c.getFilterMaxVisitCount())
                .filterMinLifetimeSpend(c.getFilterMinLifetimeSpend())
                .filterMaxLifetimeSpend(c.getFilterMaxLifetimeSpend())
                .filterLastVisitFrom(c.getFilterLastVisitFrom())
                .filterLastVisitTo(c.getFilterLastVisitTo())
                .filterWhatsappOptInOnly(c.getFilterWhatsappOptInOnly())
                .filterSmsOptInOnly(c.getFilterSmsOptInOnly())
                .recipientCount(c.getRecipientCount())
                .sentCount(c.getSentCount())
                .failedCount(c.getFailedCount())
                .skippedCount(Math.max(0, c.getRecipientCount() - c.getSentCount() - c.getFailedCount()))
                .sentAt(c.getSentAt())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
