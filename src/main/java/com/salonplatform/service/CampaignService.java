package com.salonplatform.service;

import com.salonplatform.domain.entity.CampaignRun;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.MarketingCampaign;
import com.salonplatform.domain.enums.CampaignRunStatus;
import com.salonplatform.domain.enums.CampaignStatus;
import com.salonplatform.domain.enums.MessageChannel;
import com.salonplatform.domain.entity.MessageDeliveryLog;
import com.salonplatform.domain.repository.CampaignRunRepository;
import com.salonplatform.domain.repository.CustomerRepository;
import com.salonplatform.domain.repository.MarketingCampaignRepository;
import com.salonplatform.domain.repository.MessageDeliveryLogRepository;
import com.salonplatform.dto.campaign.CampaignDeliveryResponse;
import com.salonplatform.dto.campaign.CampaignListFilter;
import com.salonplatform.dto.campaign.CampaignPreviewResponse;
import com.salonplatform.dto.campaign.CampaignRunResponse;
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

import java.time.Instant;
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
    private final CampaignRunRepository runRepository;
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
                .templateId(request.getTemplateId())
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
                .filterBranchId(request.getFilterBranchId())
                .filterMembershipFilter(request.getFilterMembershipFilter())
                .filterMembershipExpiringWithinDays(request.getFilterMembershipExpiringWithinDays())
                .filterHasServiceIds(CampaignFilterValues.serializeUuids(request.getFilterHasServiceIds()))
                .filterExcludeServiceIds(CampaignFilterValues.serializeUuids(request.getFilterExcludeServiceIds()))
                .filterHasServiceCategoryIds(CampaignFilterValues.serializeUuids(request.getFilterHasServiceCategoryIds()))
                .filterExcludeServiceCategoryIds(CampaignFilterValues.serializeUuids(request.getFilterExcludeServiceCategoryIds()))
                .filterMaxOverallRating(request.getFilterMaxOverallRating())
                .filterMinOverallRating(request.getFilterMinOverallRating())
                .filterHasSubmittedReview(request.getFilterHasSubmittedReview())
                .filterGoogleReviewNotSubmitted(request.getFilterGoogleReviewNotSubmitted())
                .filterBookingSource(request.getFilterBookingSource())
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
                .filter(c -> c.getStatus() != CampaignStatus.ARCHIVED)
                .filter(c -> matchesListFilter(c, effective))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<CampaignDeliveryResponse> listDeliveries(UUID campaignId) {
        SecurityUtils.assertBrandAdminOrAbove();
        MarketingCampaign campaign = loadCampaign(campaignId);
        List<MessageDeliveryLog> logs = deliveryLogRepository
                .findByTenantIdAndCampaignIdOrderByCreatedAtDesc(campaign.getTenantId(), campaignId);
        return mapDeliveries(logs);
    }

    public List<CampaignDeliveryResponse> listRunDeliveries(UUID campaignId, UUID runId) {
        SecurityUtils.assertBrandAdminOrAbove();
        MarketingCampaign campaign = loadCampaign(campaignId);
        CampaignRun run = loadRun(campaign, runId);
        List<MessageDeliveryLog> logs = deliveryLogRepository
                .findByTenantIdAndCampaignRunIdOrderByCreatedAtDesc(campaign.getTenantId(), run.getId());
        return mapDeliveries(logs);
    }

    public List<CampaignRunResponse> listRuns(UUID campaignId) {
        SecurityUtils.assertBrandAdminOrAbove();
        MarketingCampaign campaign = loadCampaign(campaignId);
        return runRepository.findByTenantIdAndCampaignIdOrderByStartedAtDesc(campaign.getTenantId(), campaignId)
                .stream()
                .map(this::toRunResponse)
                .toList();
    }

    private List<CampaignDeliveryResponse> mapDeliveries(List<MessageDeliveryLog> logs) {
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

    public CampaignPreviewResponse previewCampaign(UUID campaignId) {
        SecurityUtils.assertBrandAdminOrAbove();
        MarketingCampaign campaign = loadCampaign(campaignId);
        Specification<Customer> spec = buildSpec(campaign);
        long count = customerRepository.count(spec);
        List<CustomerResponse> customers = customerService.listForCampaignPreview(spec, PREVIEW_CUSTOMER_LIMIT);
        return CampaignPreviewResponse.builder()
                .matchingCustomers(count)
                .customers(customers)
                .previewTruncated(count > customers.size())
                .build();
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
        UUID tenantId = SecurityUtils.requireTenantId();
        MarketingCampaign campaign = loadCampaign(id);

        if (campaign.getStatus() == CampaignStatus.ARCHIVED) {
            throw new BadRequestException("error.campaign.archived");
        }
        if (runRepository.existsByCampaignIdAndStatus(id, CampaignRunStatus.SENDING)) {
            throw new BadRequestException("error.campaign.sendInProgress");
        }

        List<Customer> recipients = customerRepository.findAll(buildSpec(campaign));
        if (recipients.isEmpty()) {
            throw new BadRequestException("error.campaign.noRecipients");
        }

        CampaignRun run = CampaignRun.builder()
                .tenantId(tenantId)
                .campaignId(campaign.getId())
                .status(CampaignRunStatus.SENDING)
                .recipientCount(recipients.size())
                .startedAt(Instant.now())
                .build();
        run = runRepository.save(run);

        campaign.setRecipientCount(recipients.size());
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaignRepository.save(campaign);

        campaignDispatchService.dispatch(campaign.getId(), run.getId(), recipients);
        return toResponse(campaign);
    }

    @Transactional
    public void delete(UUID id) {
        SecurityUtils.assertBrandAdminOrAbove();
        MarketingCampaign campaign = loadCampaign(id);
        if (runRepository.existsByCampaignIdAndStatus(id, CampaignRunStatus.SENDING)) {
            throw new BadRequestException("error.campaign.sendInProgress");
        }

        long runCount = runRepository.countByCampaignId(id);
        if (runCount > 0) {
            campaign.setStatus(CampaignStatus.ARCHIVED);
            campaignRepository.save(campaign);
            return;
        }

        List<MessageDeliveryLog> logs = deliveryLogRepository
                .findByTenantIdAndCampaignIdOrderByCreatedAtDesc(campaign.getTenantId(), id);
        if (!logs.isEmpty()) {
            deliveryLogRepository.deleteAll(logs);
        }
        campaignRepository.delete(campaign);
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
                        ? campaign.getFilterSmsOptInOnly() : false,
                campaign.getFilterBranchId(),
                campaign.getFilterMembershipFilter(),
                campaign.getFilterMembershipExpiringWithinDays(),
                CampaignFilterValues.deserializeUuids(campaign.getFilterHasServiceIds()),
                CampaignFilterValues.deserializeUuids(campaign.getFilterExcludeServiceIds()),
                CampaignFilterValues.deserializeUuids(campaign.getFilterHasServiceCategoryIds()),
                CampaignFilterValues.deserializeUuids(campaign.getFilterExcludeServiceCategoryIds()),
                campaign.getFilterMaxOverallRating(),
                campaign.getFilterMinOverallRating(),
                campaign.getFilterHasSubmittedReview(),
                campaign.getFilterGoogleReviewNotSubmitted(),
                campaign.getFilterBookingSource());
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
                        : false,
                request.getFilterBranchId(),
                request.getFilterMembershipFilter(),
                request.getFilterMembershipExpiringWithinDays(),
                request.getFilterHasServiceIds(),
                request.getFilterExcludeServiceIds(),
                request.getFilterHasServiceCategoryIds(),
                request.getFilterExcludeServiceCategoryIds(),
                request.getFilterMaxOverallRating(),
                request.getFilterMinOverallRating(),
                request.getFilterHasSubmittedReview(),
                request.getFilterGoogleReviewNotSubmitted(),
                request.getFilterBookingSource());
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

    private CampaignRun loadRun(MarketingCampaign campaign, UUID runId) {
        CampaignRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("error.campaign.runNotFound"));
        if (!run.getCampaignId().equals(campaign.getId())
                || !run.getTenantId().equals(campaign.getTenantId())) {
            throw new ResourceNotFoundException("error.campaign.runNotFound");
        }
        return run;
    }

    private CampaignRunResponse toRunResponse(CampaignRun run) {
        return CampaignRunResponse.builder()
                .id(run.getId())
                .campaignId(run.getCampaignId())
                .status(run.getStatus())
                .recipientCount(run.getRecipientCount())
                .sentCount(run.getSentCount())
                .failedCount(run.getFailedCount())
                .skippedCount(Math.max(0, run.getRecipientCount() - run.getSentCount() - run.getFailedCount()))
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .build();
    }

    private CampaignResponse toResponse(MarketingCampaign c) {
        List<CampaignRun> runs = runRepository.findByTenantIdAndCampaignIdOrderByStartedAtDesc(
                c.getTenantId(), c.getId());
        Instant lastRunAt = runs.isEmpty() ? c.getSentAt() : runs.get(0).getStartedAt();
        boolean sendInProgress = runs.stream().anyMatch(r -> r.getStatus() == CampaignRunStatus.SENDING);
        return CampaignResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .channel(c.getChannel())
                .status(c.getStatus())
                .messageText(c.getMessageText())
                .templateId(c.getTemplateId())
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
                .filterBranchId(c.getFilterBranchId())
                .filterMembershipFilter(c.getFilterMembershipFilter())
                .filterMembershipExpiringWithinDays(c.getFilterMembershipExpiringWithinDays())
                .filterHasServiceIds(CampaignFilterValues.deserializeUuids(c.getFilterHasServiceIds()))
                .filterExcludeServiceIds(CampaignFilterValues.deserializeUuids(c.getFilterExcludeServiceIds()))
                .filterHasServiceCategoryIds(CampaignFilterValues.deserializeUuids(c.getFilterHasServiceCategoryIds()))
                .filterExcludeServiceCategoryIds(CampaignFilterValues.deserializeUuids(c.getFilterExcludeServiceCategoryIds()))
                .filterMaxOverallRating(c.getFilterMaxOverallRating())
                .filterMinOverallRating(c.getFilterMinOverallRating())
                .filterHasSubmittedReview(c.getFilterHasSubmittedReview())
                .filterGoogleReviewNotSubmitted(c.getFilterGoogleReviewNotSubmitted())
                .filterBookingSource(c.getFilterBookingSource())
                .recipientCount(c.getRecipientCount())
                .sentCount(c.getSentCount())
                .failedCount(c.getFailedCount())
                .skippedCount(Math.max(0, c.getRecipientCount() - c.getSentCount() - c.getFailedCount()))
                .sentAt(c.getSentAt())
                .createdAt(c.getCreatedAt())
                .runCount(runs.size())
                .lastRunAt(lastRunAt)
                .sendInProgress(sendInProgress)
                .build();
    }
}
