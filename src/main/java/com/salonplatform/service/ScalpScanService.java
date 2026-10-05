package com.salonplatform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.ScalpCaptureZone;
import com.salonplatform.domain.enums.ScalpLightMode;
import com.salonplatform.domain.enums.ScalpScanStatus;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.scalpscan.*;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import com.salonplatform.service.scalpscan.ScalpScanImageAnalyzer;
import com.salonplatform.service.scalpscan.ScalpScanImageMetrics;
import com.salonplatform.service.scalpscan.ScalpScanRecommendationPlanner;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScalpScanService {

    private static final int MIN_CAPTURES_FOR_ANALYSIS = 3;

    private final ScalpScanSessionRepository sessionRepository;
    private final ScalpScanCaptureRepository captureRepository;
    private final CustomerRepository customerRepository;
    private final BranchServiceRepository branchServiceRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final ScalpScanPhotoStorageService photoStorage;
    private final ScalpScanImageAnalyzer imageAnalyzer;
    private final ScalpScanRecommendationPlanner recommendationPlanner;
    private final ObjectMapper objectMapper;

    @Transactional
    public ScalpScanSessionResponse create(CreateScalpScanRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        UUID tenantId = SecurityUtils.requireTenantId();
        UUID branchId = requireManagerBranch(user);

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (!tenantId.equals(customer.getTenantId())) {
            throw new BadRequestException("Customer not in tenant");
        }
        SecurityUtils.assertBranchAccess(customer.getBranchId());

        ScalpScanSession session = ScalpScanSession.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .customerId(customer.getId())
                .performedByUserId(user.getId())
                .status(ScalpScanStatus.DRAFT)
                .build();
        session = sessionRepository.save(session);
        return toResponse(session, customer, List.of(), null);
    }

    @Transactional
    public ScalpScanCaptureDto addCapture(
            UUID sessionId,
            ScalpCaptureZone zone,
            ScalpLightMode lightMode,
            MultipartFile photo) {
        ScalpScanSession session = requireSession(sessionId);
        assertDraft(session);

        String key = photoStorage.store(session.getTenantId(), session.getBranchId(), session.getId(), photo);
        ScalpScanCapture capture = ScalpScanCapture.builder()
                .sessionId(session.getId())
                .zone(zone)
                .lightMode(lightMode)
                .imageKey(key)
                .build();
        capture = captureRepository.save(capture);
        return ScalpScanCaptureDto.builder()
                .id(capture.getId())
                .zone(capture.getZone().name())
                .lightMode(capture.getLightMode().name())
                .capturedAt(capture.getCapturedAt())
                .build();
    }

    @Transactional
    public ScalpScanSessionResponse analyze(UUID sessionId, AnalyzeScalpScanRequest request) {
        ScalpScanSession session = requireSession(sessionId);
        assertDraft(session);

        List<ScalpScanCapture> captures = captureRepository.findBySessionIdOrderByCapturedAtAsc(session.getId());
        if (captures.size() < MIN_CAPTURES_FOR_ANALYSIS) {
            throw new BadRequestException("Capture at least " + MIN_CAPTURES_FOR_ANALYSIS + " scalp photos before analysis");
        }
        long whiteCount = captures.stream().filter(c -> c.getLightMode() == ScalpLightMode.WHITE).count();
        if (whiteCount < 2) {
            throw new BadRequestException("Include at least 2 photos under white light (crown, hairline, or parting)");
        }

        Set<String> staffConfirmed = parseConcernCodes(request != null ? request.getStaffConfirmedConcerns() : null);

        double redness = 0;
        double texture = 0;
        double brightness = 0;
        double uv = 0;
        double edges = 0;
        int metricSamples = 0;
        for (ScalpScanCapture capture : captures) {
            byte[] bytes = photoStorage.load(capture.getImageKey());
            ScalpScanImageMetrics m = imageAnalyzer.analyze(bytes, capture.getLightMode());
            redness += m.rednessIndex();
            texture += m.textureVariance();
            brightness += m.meanBrightness();
            uv += m.uvFluorescenceScore();
            edges += m.edgeDensity();
            metricSamples++;
        }
        double n = Math.max(1, metricSamples);
        List<ScalpScanConcernDto> concerns = recommendationPlanner.scoreConcerns(
                redness / n, texture / n, brightness / n, uv / n, edges / n, staffConfirmed);
        if (concerns.isEmpty()) {
            concerns = List.of(ScalpScanConcernDto.builder()
                    .code("BALANCED")
                    .label("Balanced scalp")
                    .severity("LOW")
                    .score(30)
                    .insight("No major concerns detected — focus on maintenance and protection.")
                    .build());
        }
        ScalpScanMetricsDto metrics = recommendationPlanner.aggregateMetrics(
                redness / n, texture / n, brightness / n, uv / n, edges / n, concerns);
        ScalpScanReportDto report = recommendationPlanner.buildReport(
                metrics, concerns, loadCatalogRefs(session.getBranchId(), session.getTenantId()));

        session.setStatus(ScalpScanStatus.COMPLETE);
        session.setAnalyzedAt(Instant.now());
        session.setScalpHealthScore(metrics.getScalpHealthScore());
        session.setPrimaryConcernCode(concerns.get(0).getCode());
        if (request != null && request.getStaffNotes() != null) {
            session.setStaffNotes(request.getStaffNotes().trim());
        }
        session.setReportJson(writeJson(report));
        sessionRepository.save(session);

        Customer customer = customerRepository.findById(session.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return toResponse(session, customer, captures, report);
    }

    @Transactional(readOnly = true)
    public ScalpScanSessionResponse get(UUID sessionId) {
        ScalpScanSession session = requireSession(sessionId);
        Customer customer = customerRepository.findById(session.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        List<ScalpScanCapture> captures = captureRepository.findBySessionIdOrderByCapturedAtAsc(session.getId());
        ScalpScanReportDto report = readReport(session.getReportJson());
        return toResponse(session, customer, captures, report);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScalpScanSessionResponse> list(UUID branchId, UUID customerId, int page, int size) {
        UserPrincipal user = SecurityUtils.currentUser();
        UUID tenantId = SecurityUtils.requireTenantId();
        UUID effectiveBranch = branchId != null ? branchId : user.getBranchId();
        if (effectiveBranch == null) {
            throw new BadRequestException("Branch is required");
        }
        SecurityUtils.assertBranchAccess(effectiveBranch);

        Page<ScalpScanSession> result;
        PageRequest pageable = PageRequest.of(page, size);
        if (customerId != null) {
            result = sessionRepository.findByTenantIdAndBranchIdAndCustomerIdOrderByCreatedAtDesc(
                    tenantId, effectiveBranch, customerId, pageable);
        } else {
            result = sessionRepository.findByTenantIdAndBranchIdOrderByCreatedAtDesc(
                    tenantId, effectiveBranch, pageable);
        }

        Map<UUID, Customer> customers = loadCustomers(result.getContent());
        List<ScalpScanSessionResponse> items = result.getContent().stream()
                .map(s -> {
                    Customer c = customers.get(s.getCustomerId());
                    ScalpScanReportDto report = readReport(s.getReportJson());
                    long captureCount = captureRepository.countBySessionId(s.getId());
                    return summaryResponse(s, c, captureCount, report);
                })
                .toList();
        return PageResponse.<ScalpScanSessionResponse>builder()
                .content(items)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    public ScalpScanCapture requireCapture(UUID captureId) {
        return captureRepository.findById(captureId)
                .orElseThrow(() -> new ResourceNotFoundException("Capture not found"));
    }

    public void assertCaptureAccess(ScalpScanCapture capture) {
        ScalpScanSession session = requireSession(capture.getSessionId());
        requireSession(session.getId());
    }

    public String photoKeyForCapture(UUID captureId) {
        ScalpScanCapture capture = requireCapture(captureId);
        requireSession(capture.getSessionId());
        return capture.getImageKey();
    }

    private ScalpScanSession requireSession(UUID sessionId) {
        ScalpScanSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Scalp scan not found"));
        UUID tenantId = SecurityUtils.requireTenantId();
        if (!tenantId.equals(session.getTenantId())) {
            throw new ResourceNotFoundException("Scalp scan not found");
        }
        SecurityUtils.assertBranchAccess(session.getBranchId());
        return session;
    }

    private static UUID requireManagerBranch(UserPrincipal user) {
        if (user.getBranchId() == null) {
            throw new BadRequestException("Branch context required");
        }
        SecurityUtils.assertBranchAccess(user.getBranchId());
        return user.getBranchId();
    }

    private static void assertDraft(ScalpScanSession session) {
        if (session.getStatus() != ScalpScanStatus.DRAFT) {
            throw new BadRequestException("Scan is already complete");
        }
    }

    private List<ScalpScanRecommendationPlanner.CatalogServiceRef> loadCatalogRefs(UUID branchId, UUID tenantId) {
        List<BranchService> branchServices = branchServiceRepository.findByBranchIdAndActiveTrue(branchId);
        if (branchServices.isEmpty()) {
            return List.of();
        }
        Set<UUID> serviceIds = branchServices.stream().map(BranchService::getServiceId).collect(Collectors.toSet());
        Map<UUID, SalonService> salonById = salonServiceRepository.findAllById(serviceIds).stream()
                .filter(s -> tenantId.equals(s.getTenantId()))
                .collect(Collectors.toMap(SalonService::getId, s -> s));
        List<ScalpScanRecommendationPlanner.CatalogServiceRef> refs = new ArrayList<>();
        for (BranchService bs : branchServices) {
            SalonService salon = salonById.get(bs.getServiceId());
            if (salon == null || !salon.isActive()) {
                continue;
            }
            String name = bs.getDisplayNameOverride() != null && !bs.getDisplayNameOverride().isBlank()
                    ? bs.getDisplayNameOverride()
                    : salon.getName();
            refs.add(new ScalpScanRecommendationPlanner.CatalogServiceRef(bs.getId(), name));
        }
        return refs;
    }

    private Map<UUID, Customer> loadCustomers(List<ScalpScanSession> sessions) {
        Set<UUID> ids = sessions.stream().map(ScalpScanSession::getCustomerId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return customerRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Customer::getId, c -> c));
    }

    private ScalpScanSessionResponse toResponse(
            ScalpScanSession session,
            Customer customer,
            List<ScalpScanCapture> captures,
            ScalpScanReportDto report) {
        List<ScalpScanCaptureDto> captureDtos = captures.stream()
                .map(c -> ScalpScanCaptureDto.builder()
                        .id(c.getId())
                        .zone(c.getZone().name())
                        .lightMode(c.getLightMode().name())
                        .capturedAt(c.getCapturedAt())
                        .build())
                .toList();
        return ScalpScanSessionResponse.builder()
                .id(session.getId())
                .customerId(customer.getId())
                .customerName(customer.getName())
                .customerPhone(customer.getPhone())
                .branchId(session.getBranchId())
                .status(session.getStatus().name())
                .scalpHealthScore(session.getScalpHealthScore())
                .primaryConcernCode(session.getPrimaryConcernCode())
                .staffNotes(session.getStaffNotes())
                .createdAt(session.getCreatedAt())
                .analyzedAt(session.getAnalyzedAt())
                .captureCount(captures.size())
                .report(report)
                .captures(captureDtos)
                .build();
    }

    private ScalpScanSessionResponse summaryResponse(
            ScalpScanSession session,
            Customer customer,
            long captureCount,
            ScalpScanReportDto report) {
        return ScalpScanSessionResponse.builder()
                .id(session.getId())
                .customerId(session.getCustomerId())
                .customerName(customer != null ? customer.getName() : "—")
                .customerPhone(customer != null ? customer.getPhone() : null)
                .branchId(session.getBranchId())
                .status(session.getStatus().name())
                .scalpHealthScore(session.getScalpHealthScore())
                .primaryConcernCode(session.getPrimaryConcernCode())
                .createdAt(session.getCreatedAt())
                .analyzedAt(session.getAnalyzedAt())
                .captureCount((int) captureCount)
                .report(report != null ? ScalpScanReportDto.builder()
                        .metrics(report.getMetrics())
                        .concerns(report.getConcerns())
                        .build() : null)
                .build();
    }

    private String writeJson(ScalpScanReportDto report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Failed to save report");
        }
    }

    private ScalpScanReportDto readReport(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ScalpScanReportDto.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static Set<String> parseConcernCodes(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
