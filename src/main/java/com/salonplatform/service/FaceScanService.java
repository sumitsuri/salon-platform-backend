package com.salonplatform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.*;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.facescan.*;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import com.salonplatform.dto.scan.ScanAnalysisMetaDto;
import com.salonplatform.service.scan.ScanAnalysisSupport;
import com.salonplatform.service.scan.ScanCaptureQuality;
import com.salonplatform.service.scan.ScanLlmReportMapper;
import com.salonplatform.service.scan.ScanBranchCatalogService;
import com.salonplatform.service.scan.ScanReportCatalogGuard;
import com.salonplatform.service.scan.ScanVisionLlmService;
import com.salonplatform.service.facescan.FaceScanImageAnalysis;
import com.salonplatform.service.facescan.FaceScanImageAnalyzer;
import com.salonplatform.service.facescan.FaceScanImageMetrics;
import com.salonplatform.service.facescan.FaceScanRecommendationPlanner;
import com.fasterxml.jackson.databind.JsonNode;
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
public class FaceScanService {

    private final FaceScanSessionRepository sessionRepository;
    private final FaceScanCaptureRepository captureRepository;
    private final CustomerRepository customerRepository;
    private final FaceScanPhotoStorageService photoStorage;
    private final FaceScanImageAnalyzer imageAnalyzer;
    private final FaceScanRecommendationPlanner recommendationPlanner;
    private final ScanVisionLlmService scanVisionLlmService;
    private final ScanBranchCatalogService scanBranchCatalogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public FaceScanSessionResponse create(CreateFaceScanRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        UUID tenantId = SecurityUtils.requireTenantId();
        UUID branchId = requireManagerBranch(user);

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (!tenantId.equals(customer.getTenantId())) {
            throw new BadRequestException("Customer not in tenant");
        }
        SecurityUtils.assertBranchAccess(customer.getBranchId());

        FaceScanSession session = FaceScanSession.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .customerId(customer.getId())
                .performedByUserId(user.getId())
                .status(FaceScanStatus.DRAFT)
                .build();
        session = sessionRepository.save(session);
        return toResponse(session, customer, List.of(), null);
    }

    @Transactional
    public FaceScanCaptureDto addCapture(
            UUID sessionId,
            FaceCaptureZone zone,
            FaceLightMode lightMode,
            MultipartFile photo) {
        FaceScanSession session = requireSession(sessionId);
        assertDraft(session);

        String key = photoStorage.store(session.getTenantId(), session.getBranchId(), session.getId(), photo);
        FaceScanCapture capture = FaceScanCapture.builder()
                .sessionId(session.getId())
                .zone(zone)
                .lightMode(lightMode)
                .imageKey(key)
                .build();
        capture = captureRepository.save(capture);
        return FaceScanCaptureDto.builder()
                .id(capture.getId())
                .zone(capture.getZone().name())
                .lightMode(capture.getLightMode().name())
                .capturedAt(capture.getCapturedAt())
                .build();
    }

    @Transactional
    public FaceScanSessionResponse analyze(UUID sessionId, AnalyzeFaceScanRequest request) {
        FaceScanSession session = requireSession(sessionId);
        assertDraft(session);

        List<FaceScanCapture> captures = captureRepository.findBySessionIdOrderByCapturedAtAsc(session.getId());
        if (captures.isEmpty()) {
            throw new BadRequestException("Add at least one face photo before analysis");
        }

        Set<String> staffConfirmed = parseConcernCodes(request != null ? request.getStaffConfirmedConcerns() : null);
        String staffNotes = request != null ? request.getStaffNotes() : null;

        double redness = 0;
        double texture = 0;
        double brightness = 0;
        double uv = 0;
        double weightSum = 0;
        List<ScanCaptureQuality> qualities = new ArrayList<>();
        List<byte[]> llmImages = new ArrayList<>();
        List<String> zoneLabels = new ArrayList<>();
        for (FaceScanCapture capture : captures) {
            byte[] bytes = photoStorage.load(capture.getImageKey());
            llmImages.add(bytes);
            zoneLabels.add(capture.getZone().name());
            FaceScanImageAnalysis analysis = imageAnalyzer.analyze(bytes, capture.getLightMode(), capture.getZone());
            qualities.add(analysis.quality());
            FaceScanImageMetrics m = analysis.metrics();
            double w = faceZoneWeight(capture.getZone());
            redness += m.rednessIndex() * w;
            texture += m.textureVariance() * w;
            brightness += m.meanBrightness() * w;
            uv += m.uvFluorescenceScore() * w;
            weightSum += w;
        }
        double n = Math.max(1, weightSum);
        ScanAnalysisSupport.assertPhotoQuality(staffConfirmed, qualities);

        List<FaceScanConcernDto> concerns = recommendationPlanner.scoreConcerns(
                redness / n, texture / n, brightness / n, uv / n, staffConfirmed);
        if (concerns.isEmpty()) {
            concerns = List.of(FaceScanConcernDto.builder()
                    .code("BALANCED")
                    .label("Balanced skin")
                    .severity("LOW")
                    .score(30)
                    .insight("No major concerns detected — focus on SPF and gentle maintenance.")
                    .build());
        }
        FaceScanMetricsDto metrics = recommendationPlanner.aggregateMetrics(
                redness / n, texture / n, brightness / n, concerns);
        List<FaceScanRecommendationPlanner.CatalogServiceRef> catalog =
                scanBranchCatalogService.loadFaceCatalog(session.getBranchId(), session.getTenantId());
        FaceScanReportDto report = recommendationPlanner.buildReport(metrics, concerns, catalog);

        Optional<JsonNode> llm = scanVisionLlmService.analyzeFace(
                llmImages,
                zoneLabels,
                staffConfirmed,
                staffNotes,
                catalog.stream().map(FaceScanRecommendationPlanner.CatalogServiceRef::name).toList());
        boolean llmUsed = llm.isPresent();
        llm.ifPresent(node -> ScanLlmReportMapper.mergeFace(report, node));
        ScanReportCatalogGuard.enforceFace(
                report,
                ScanReportCatalogGuard.namesLowerFromStrings(
                        catalog.stream().map(FaceScanRecommendationPlanner.CatalogServiceRef::name).toList()));

        ScanAnalysisMetaDto meta = ScanAnalysisSupport.buildMeta(qualities, !staffConfirmed.isEmpty(), llmUsed);
        report.setAnalysisMeta(meta);
        recommendationPlanner.trimForConfidence(report, meta.getConfidence());

        session.setStatus(FaceScanStatus.COMPLETE);
        session.setAnalyzedAt(Instant.now());
        session.setSkinHealthScore(metrics.getSkinHealthScore());
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
    public FaceScanSessionResponse get(UUID sessionId) {
        FaceScanSession session = requireSession(sessionId);
        Customer customer = customerRepository.findById(session.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        List<FaceScanCapture> captures = captureRepository.findBySessionIdOrderByCapturedAtAsc(session.getId());
        return toResponse(session, customer, captures, readReport(session.getReportJson()));
    }

    @Transactional(readOnly = true)
    public PageResponse<FaceScanSessionResponse> list(UUID branchId, UUID customerId, int page, int size) {
        UUID tenantId = SecurityUtils.requireTenantId();
        UserPrincipal user = SecurityUtils.currentUser();
        UUID effectiveBranch = branchId != null ? branchId : user.getBranchId();
        if (effectiveBranch == null) {
            throw new BadRequestException("Branch is required");
        }
        SecurityUtils.assertBranchAccess(effectiveBranch);

        PageRequest pageable = PageRequest.of(page, size);
        Page<FaceScanSession> result = customerId != null
                ? sessionRepository.findByTenantIdAndBranchIdAndCustomerIdOrderByCreatedAtDesc(
                        tenantId, effectiveBranch, customerId, pageable)
                : sessionRepository.findByTenantIdAndBranchIdOrderByCreatedAtDesc(
                        tenantId, effectiveBranch, pageable);

        Map<UUID, Customer> customers = loadCustomers(result.getContent());
        List<FaceScanSessionResponse> items = result.getContent().stream()
                .map(s -> {
                    Customer c = customers.get(s.getCustomerId());
                    FaceScanReportDto report = readReport(s.getReportJson());
                    long captureCount = captureRepository.countBySessionId(s.getId());
                    return summaryResponse(s, c, captureCount, report);
                })
                .toList();
        return PageResponse.<FaceScanSessionResponse>builder()
                .content(items)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    public String photoKeyForCapture(UUID captureId) {
        FaceScanCapture capture = captureRepository.findById(captureId)
                .orElseThrow(() -> new ResourceNotFoundException("Capture not found"));
        requireSession(capture.getSessionId());
        return capture.getImageKey();
    }

    private FaceScanSession requireSession(UUID sessionId) {
        FaceScanSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Face scan not found"));
        UUID tenantId = SecurityUtils.requireTenantId();
        if (!tenantId.equals(session.getTenantId())) {
            throw new ResourceNotFoundException("Face scan not found");
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

    private static void assertDraft(FaceScanSession session) {
        if (session.getStatus() != FaceScanStatus.DRAFT) {
            throw new BadRequestException("Scan is already complete");
        }
    }

    private Map<UUID, Customer> loadCustomers(List<FaceScanSession> sessions) {
        Set<UUID> ids = sessions.stream().map(FaceScanSession::getCustomerId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return customerRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Customer::getId, c -> c));
    }

    private FaceScanSessionResponse toResponse(
            FaceScanSession session,
            Customer customer,
            List<FaceScanCapture> captures,
            FaceScanReportDto report) {
        List<FaceScanCaptureDto> captureDtos = captures.stream()
                .map(c -> FaceScanCaptureDto.builder()
                        .id(c.getId())
                        .zone(c.getZone().name())
                        .lightMode(c.getLightMode().name())
                        .capturedAt(c.getCapturedAt())
                        .build())
                .toList();
        return FaceScanSessionResponse.builder()
                .id(session.getId())
                .customerId(customer.getId())
                .customerName(customer.getName())
                .customerPhone(customer.getPhone())
                .branchId(session.getBranchId())
                .status(session.getStatus().name())
                .skinHealthScore(session.getSkinHealthScore())
                .primaryConcernCode(session.getPrimaryConcernCode())
                .staffNotes(session.getStaffNotes())
                .createdAt(session.getCreatedAt())
                .analyzedAt(session.getAnalyzedAt())
                .captureCount(captures.size())
                .report(report)
                .captures(captureDtos)
                .build();
    }

    private FaceScanSessionResponse summaryResponse(
            FaceScanSession session,
            Customer customer,
            long captureCount,
            FaceScanReportDto report) {
        return FaceScanSessionResponse.builder()
                .id(session.getId())
                .customerId(session.getCustomerId())
                .customerName(customer != null ? customer.getName() : "—")
                .customerPhone(customer != null ? customer.getPhone() : null)
                .branchId(session.getBranchId())
                .status(session.getStatus().name())
                .skinHealthScore(session.getSkinHealthScore())
                .primaryConcernCode(session.getPrimaryConcernCode())
                .createdAt(session.getCreatedAt())
                .analyzedAt(session.getAnalyzedAt())
                .captureCount((int) captureCount)
                .report(report != null ? FaceScanReportDto.builder()
                        .metrics(report.getMetrics())
                        .concerns(report.getConcerns())
                        .build() : null)
                .build();
    }

    private String writeJson(FaceScanReportDto report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Failed to save report");
        }
    }

    private FaceScanReportDto readReport(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, FaceScanReportDto.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static double faceZoneWeight(FaceCaptureZone zone) {
        return zone == FaceCaptureZone.FULL_FACE ? 1.0 : 1.2;
    }

    private static Set<String> parseConcernCodes(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
