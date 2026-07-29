package com.salonplatform.sales.application;

import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ForbiddenException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.sales.domain.entity.*;
import com.salonplatform.sales.domain.enums.*;
import com.salonplatform.sales.domain.port.ProvisionMode;
import com.salonplatform.sales.domain.port.TenantProvisionResult;
import com.salonplatform.sales.domain.port.TenantProvisioningPort;
import com.salonplatform.sales.domain.repository.*;
import com.salonplatform.sales.dto.*;
import com.salonplatform.sales.repository.SalesLeadSpecifications;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesLeadService {

    private final SalesLeadRepository leadRepository;
    private final SalesActivityRepository activityRepository;
    private final SalesStageHistoryRepository stageHistoryRepository;
    private final SalesLocalityRepository localityRepository;
    private final UserRepository userRepository;
    private final SalesIncentiveService incentiveService;

    @Transactional
    public SalesLeadResponse create(CreateSalesLeadRequest request) {
        SecurityUtils.assertSalesAccess();
        UUID repId = resolveAssignedRep(request.getAssignedRepId());

        SalesLead lead = SalesLead.builder()
                .businessName(request.getBusinessName().trim())
                .contactName(request.getContactName().trim())
                .email(normalizeEmail(request.getEmail()))
                .phone(request.getPhone().trim())
                .leadType(request.getLeadType())
                .source(request.getSource() != null ? request.getSource() : LeadSource.FIELD)
                .localityId(request.getLocalityId())
                .localityName(resolveLocalityName(request.getLocalityId(), request.getLocalityName()))
                .address(request.getAddress())
                .city(request.getCity() != null ? request.getCity() : "Bangalore")
                .expectedBranches(request.getExpectedBranches() != null ? request.getExpectedBranches() : 1)
                .useCase(request.getUseCase())
                .notes(request.getNotes())
                .assignedRepId(repId)
                .nextFollowUpAt(request.getNextFollowUpAt())
                .build();

        SalesLead saved = leadRepository.save(lead);
        recordStageChange(saved.getId(), null, LeadStage.NEW, SecurityUtils.currentUserId(), "Lead created");
        return toResponse(saved);
    }

    @Transactional
    public SalesLeadResponse createPublic(CreateSalesLeadRequest request) {
        UUID repId = request.getAssignedRepId();
        SalesLead lead = SalesLead.builder()
                .businessName(request.getBusinessName().trim())
                .contactName(request.getContactName().trim())
                .email(normalizeEmail(request.getEmail()))
                .phone(request.getPhone().trim())
                .leadType(request.getLeadType())
                .source(request.getSource() != null ? request.getSource() : LeadSource.MARKETING_WEB)
                .localityName(request.getLocalityName())
                .expectedBranches(request.getExpectedBranches() != null ? request.getExpectedBranches() : 1)
                .useCase(request.getUseCase())
                .notes(request.getNotes())
                .assignedRepId(repId)
                .build();
        SalesLead saved = leadRepository.save(lead);
        UUID actor = repId != null ? repId : saved.getId();
        recordStageChange(saved.getId(), null, LeadStage.NEW, actor, "Lead created from marketing site");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesLeadResponse> list(SalesLeadListFilter filter) {
        SecurityUtils.assertSalesAccess();
        boolean isSalesExec = SecurityUtils.isSalesExecutive();
        if (isSalesExec) {
            filter.setMineOnly(true);
        }

        int page = PageUtils.normalizePage(filter.getPage());
        int size = PageUtils.normalizeSize(filter.getSize());

        if (filter.getLocalityId() != null) {
            localityRepository.findById(filter.getLocalityId())
                    .ifPresent(loc -> filter.setLocalityName(loc.getName()));
        }

        Page<SalesLead> result = leadRepository.findAll(
                SalesLeadSpecifications.fromFilter(filter, SecurityUtils.currentUserId(), isSalesExec),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );

        return PageResponse.<SalesLeadResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public List<SalesLeadResponse> listPipelineBoard() {
        SecurityUtils.assertSalesAccess();
        List<SalesLeadResponse> all = new ArrayList<>();
        int page = 0;
        final int batchSize = 100;
        final int maxRows = 500;
        while (all.size() < maxRows) {
            PageResponse<SalesLeadResponse> batch = list(
                    SalesLeadListFilter.builder().page(page).size(batchSize).build());
            all.addAll(batch.getContent());
            if (batch.getContent().isEmpty() || page + 1 >= batch.getTotalPages()) {
                break;
            }
            page++;
        }
        return all.size() > maxRows ? all.subList(0, maxRows) : all;
    }

    @Transactional(readOnly = true)
    public List<SalesLeadResponse> listByStage(LeadStage stage) {
        SecurityUtils.assertSalesAccess();
        SalesLeadListFilter filter = SalesLeadListFilter.builder().stage(stage).page(0).size(500).build();
        return list(filter).getContent();
    }

    @Transactional(readOnly = true)
    public SalesLeadResponse get(UUID id) {
        SecurityUtils.assertSalesAccess();
        return toResponse(requireLeadWithAccess(id));
    }

    @Transactional
    public SalesLeadResponse update(UUID id, UpdateSalesLeadRequest request) {
        SecurityUtils.assertSalesAccess();
        SalesLead lead = requireLeadWithAccess(id);

        if (request.getLocalityId() != null) {
            lead.setLocalityId(request.getLocalityId());
            lead.setLocalityName(resolveLocalityName(request.getLocalityId(), request.getLocalityName()));
        } else if (request.getLocalityName() != null) {
            lead.setLocalityName(request.getLocalityName());
        }
        if (request.getLeadType() != null) {
            lead.setLeadType(request.getLeadType());
        }
        if (request.getUseCase() != null) {
            lead.setUseCase(request.getUseCase().trim());
        }
        if (request.getNotes() != null) {
            lead.setNotes(request.getNotes());
        }
        if (request.getExpectedBranches() != null) {
            lead.setExpectedBranches(request.getExpectedBranches());
        }
        boolean pricingTouched = request.getQuotedAmount() != null
                || request.getBillingPeriod() != null
                || request.getDiscountPercent() != null
                || request.getDiscountAmount() != null
                || request.getFinalPaidAmount() != null;
        if (pricingTouched) {
            SalesPricingUtils.syncPricing(lead, request);
        }

        return toResponse(leadRepository.save(lead));
    }

    @Transactional
    public SalesLeadResponse updateStage(UUID id, UpdateSalesLeadStageRequest request) {
        SecurityUtils.assertSalesAccess();
        SalesLead lead = requireLeadWithAccess(id);
        LeadStage from = lead.getStage();
        LeadStage to = request.getStage();

        SalesStageTransitionValidator.validateTransition(from, to);

        if (to == LeadStage.LOST) {
            if (request.getLostReason() == null || request.getLostReason().isBlank()) {
                throw new BadRequestException("Lost reason is required");
            }
            lead.setLostReason(request.getLostReason().trim());
        }

        if (to == LeadStage.WON) {
            if (request.getNotes() == null || request.getNotes().isBlank()) {
                throw new BadRequestException("Notes are required when marking lead as Won");
            }
            lead.setConvertedAt(Instant.now());
            if (lead.getProjectedMrr() == null) {
                BigDecimal revenue = SalesPricingUtils.effectiveRevenueAmount(lead);
                if (revenue != null) {
                    lead.setProjectedMrr(SalesPricingUtils.monthlyEquivalent(
                            revenue, lead.getBillingPeriod()));
                }
            }
            incentiveService.recordIncentiveIfEligible(lead, IncentiveEventType.WON);
        }

        if (to.ordinal() >= LeadStage.QUALIFIED.ordinal()) {
            SalesStageTransitionValidator.validateQualifiedFields(
                    lead.getUseCase(), lead.getLeadType());
        }

        if (to == LeadStage.CONTACTED || (from == LeadStage.NEW && to.ordinal() > LeadStage.CONTACTED.ordinal())) {
            long activityCount = activityRepository.findByLeadIdOrderByCreatedAtDesc(id).size();
            if (activityCount == 0 && to != LeadStage.LOST) {
                throw new BadRequestException("Log at least one activity before moving past NEW");
            }
        }

        if (to == LeadStage.FREE_TRIAL) {
            lead.setTrialIntentAt(Instant.now());
            incentiveService.recordIncentiveIfEligible(lead, IncentiveEventType.FREE_TRIAL);
        }

        lead.setStage(to);
        SalesLead saved = leadRepository.save(lead);
        recordStageChange(id, from, to, SecurityUtils.currentUserId(), request.getNotes());
        return toResponse(saved);
    }

    @Transactional
    public SalesActivityResponse addActivity(UUID leadId, CreateSalesActivityRequest request) {
        SecurityUtils.assertSalesAccess();
        requireLeadWithAccess(leadId);

        SalesActivity activity = SalesActivity.builder()
                .leadId(leadId)
                .repId(SecurityUtils.currentUserId())
                .activityType(request.getActivityType())
                .notes(request.getNotes())
                .activityAt(request.getActivityAt() != null ? request.getActivityAt() : Instant.now())
                .build();

        SalesActivity saved = activityRepository.save(activity);
        return toActivityResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SalesActivityResponse> listActivities(UUID leadId) {
        SecurityUtils.assertSalesAccess();
        requireLeadWithAccess(leadId);
        return activityRepository.findByLeadIdOrderByCreatedAtDesc(leadId).stream()
                .map(this::toActivityResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SalesStageHistoryResponse> listStageHistory(UUID leadId) {
        SecurityUtils.assertSalesAccess();
        requireLeadWithAccess(leadId);
        return stageHistoryRepository.findByLeadIdOrderByCreatedAtDesc(leadId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SalesLocalityResponse> listLocalities() {
        SecurityUtils.assertSalesAccess();
        return localityRepository.findByActiveTrueOrderByZoneAscNameAsc().stream()
                .map(l -> SalesLocalityResponse.builder().id(l.getId()).name(l.getName()).zone(l.getZone()).build())
                .toList();
    }

    private UUID resolveAssignedRep(UUID requested) {
        if (SecurityUtils.isSalesExecutive()) {
            return SecurityUtils.currentUserId();
        }
        if (requested != null) {
            return requested;
        }
        return SecurityUtils.currentUserId();
    }

    private SalesLead requireLeadWithAccess(UUID id) {
        SalesLead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
        if (SecurityUtils.isSalesExecutive()
                && !SecurityUtils.currentUserId().equals(lead.getAssignedRepId())) {
            throw new ForbiddenException("Access denied for this lead");
        }
        return lead;
    }

    private void recordStageChange(UUID leadId, LeadStage from, LeadStage to, UUID userId, String notes) {
        stageHistoryRepository.save(SalesStageHistory.builder()
                .leadId(leadId)
                .fromStage(from)
                .toStage(to)
                .changedByUserId(userId)
                .notes(notes)
                .build());
    }

    private String resolveLocalityName(UUID localityId, String fallback) {
        if (localityId != null) {
            return localityRepository.findById(localityId).map(SalesLocality::getName).orElse(fallback);
        }
        return fallback;
    }

    private String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    SalesLeadResponse toResponse(SalesLead lead) {
        String repName = lead.getAssignedRepId() != null
                ? userRepository.findById(lead.getAssignedRepId()).map(User::getName).orElse(null)
                : null;
        return SalesLeadResponse.builder()
                .id(lead.getId())
                .businessName(lead.getBusinessName())
                .contactName(lead.getContactName())
                .email(lead.getEmail())
                .phone(lead.getPhone())
                .leadType(lead.getLeadType())
                .stage(lead.getStage())
                .source(lead.getSource())
                .localityId(lead.getLocalityId())
                .localityName(lead.getLocalityName())
                .address(lead.getAddress())
                .city(lead.getCity())
                .expectedBranches(lead.getExpectedBranches())
                .useCase(lead.getUseCase())
                .notes(lead.getNotes())
                .assignedRepId(lead.getAssignedRepId())
                .assignedRepName(repName)
                .convertedTenantId(lead.getConvertedTenantId())
                .projectedMrr(lead.getProjectedMrr())
                .planTier(lead.getPlanTier())
                .quotedAmount(lead.getQuotedAmount())
                .billingPeriod(lead.getBillingPeriod())
                .discountPercent(lead.getDiscountPercent())
                .discountAmount(lead.getDiscountAmount())
                .finalPaidAmount(lead.getFinalPaidAmount())
                .lostReason(lead.getLostReason())
                .trialIntentAt(lead.getTrialIntentAt())
                .convertedAt(lead.getConvertedAt())
                .nextFollowUpAt(lead.getNextFollowUpAt())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .build();
    }

    private SalesActivityResponse toActivityResponse(SalesActivity a) {
        String repName = userRepository.findById(a.getRepId()).map(User::getName).orElse(null);
        return SalesActivityResponse.builder()
                .id(a.getId())
                .leadId(a.getLeadId())
                .repId(a.getRepId())
                .repName(repName)
                .activityType(a.getActivityType())
                .notes(a.getNotes())
                .activityAt(a.getActivityAt())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private SalesStageHistoryResponse toHistoryResponse(SalesStageHistory h) {
        String name = userRepository.findById(h.getChangedByUserId()).map(User::getName).orElse(null);
        return SalesStageHistoryResponse.builder()
                .id(h.getId())
                .fromStage(h.getFromStage())
                .toStage(h.getToStage())
                .changedByUserId(h.getChangedByUserId())
                .changedByName(name)
                .notes(h.getNotes())
                .createdAt(h.getCreatedAt())
                .build();
    }

    static LocalDate currentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
