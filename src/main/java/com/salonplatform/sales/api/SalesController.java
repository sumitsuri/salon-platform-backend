package com.salonplatform.sales.api;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.sales.domain.SalesUseCases;
import com.salonplatform.sales.application.*;
import com.salonplatform.sales.domain.enums.LeadSource;
import com.salonplatform.sales.domain.enums.LeadStage;
import com.salonplatform.sales.domain.enums.LeadType;
import com.salonplatform.sales.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesLeadService leadService;
    private final SalesConversionService conversionService;
    private final SalesRepService repService;
    private final SalesIncentiveService incentiveService;
    private final SalesAnalyticsService analyticsService;

    @GetMapping("/leads")
    public ApiResponse<PageResponse<SalesLeadResponse>> listLeads(
            @RequestParam(required = false) LeadStage stage,
            @RequestParam(required = false) LeadType leadType,
            @RequestParam(required = false) LeadSource source,
            @RequestParam(required = false) UUID assignedRepId,
            @RequestParam(required = false) List<UUID> assignedRepIds,
            @RequestParam(required = false) UUID localityId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SalesLeadListFilter filter = SalesLeadListFilter.builder()
                .stage(stage).leadType(leadType).source(source)
                .assignedRepId(assignedRepId).assignedRepIds(assignedRepIds)
                .localityId(localityId).search(search)
                .createdFrom(createdFrom).createdTo(createdTo)
                .page(page).size(size).build();
        return ApiResponse.ok(leadService.list(filter));
    }

    @GetMapping("/leads/pipeline")
    public ApiResponse<List<SalesLeadResponse>> pipelineBoard() {
        return ApiResponse.ok(leadService.listPipelineBoard());
    }

    @PostMapping("/leads")
    public ApiResponse<SalesLeadResponse> createLead(@Valid @RequestBody CreateSalesLeadRequest request) {
        return ApiResponse.ok(leadService.create(request));
    }

    @GetMapping("/leads/{id}")
    public ApiResponse<SalesLeadResponse> getLead(@PathVariable UUID id) {
        return ApiResponse.ok(leadService.get(id));
    }

    @PatchMapping("/leads/{id}")
    public ApiResponse<SalesLeadResponse> updateLead(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSalesLeadRequest request) {
        return ApiResponse.ok(leadService.update(id, request));
    }

    @PatchMapping("/leads/{id}/stage")
    public ApiResponse<SalesLeadResponse> updateStage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSalesLeadStageRequest request) {
        return ApiResponse.ok(leadService.updateStage(id, request));
    }

    @PostMapping("/leads/{id}/activities")
    public ApiResponse<SalesActivityResponse> addActivity(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSalesActivityRequest request) {
        return ApiResponse.ok(leadService.addActivity(id, request));
    }

    @GetMapping("/leads/{id}/activities")
    public ApiResponse<List<SalesActivityResponse>> listActivities(@PathVariable UUID id) {
        return ApiResponse.ok(leadService.listActivities(id));
    }

    @GetMapping("/leads/{id}/history")
    public ApiResponse<List<SalesStageHistoryResponse>> listHistory(@PathVariable UUID id) {
        return ApiResponse.ok(leadService.listStageHistory(id));
    }

    @PostMapping("/leads/{id}/convert")
    public ApiResponse<SalesLeadResponse> convert(
            @PathVariable UUID id,
            @Valid @RequestBody ConvertSalesLeadRequest request) {
        return ApiResponse.ok(conversionService.convert(id, request));
    }

    @GetMapping("/localities")
    public ApiResponse<List<SalesLocalityResponse>> localities() {
        return ApiResponse.ok(leadService.listLocalities());
    }

    @GetMapping("/use-cases")
    public ApiResponse<List<String>> useCases() {
        return ApiResponse.ok(SalesUseCases.PREDEFINED);
    }

    @GetMapping("/reps")
    public ApiResponse<List<SalesRepResponse>> listReps(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(repService.listReps(includeInactive));
    }

    @PostMapping("/reps")
    public ApiResponse<SalesRepResponse> createRep(@Valid @RequestBody CreateSalesRepRequest request) {
        return ApiResponse.ok(repService.createRep(request));
    }

    @PatchMapping("/reps/{id}")
    public ApiResponse<SalesRepResponse> updateRep(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSalesRepRequest request) {
        return ApiResponse.ok(repService.updateRep(id, request));
    }

    @DeleteMapping("/reps/{id}")
    public ApiResponse<Void> deactivateRep(@PathVariable UUID id) {
        repService.deactivateRep(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/targets")
    public ApiResponse<List<SalesTargetResponse>> listTargets(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(repService.listTargets(weekStart, from, to));
    }

    @PutMapping("/targets")
    public ApiResponse<SalesTargetResponse> upsertTarget(@Valid @RequestBody UpsertSalesTargetRequest request) {
        return ApiResponse.ok(repService.upsertTarget(request));
    }

    @GetMapping("/incentives/rules")
    public ApiResponse<List<IncentiveRuleResponse>> listIncentiveRules() {
        return ApiResponse.ok(incentiveService.listRules());
    }

    @PutMapping("/incentives/rules")
    public ApiResponse<IncentiveRuleResponse> upsertIncentiveRule(@Valid @RequestBody UpsertIncentiveRuleRequest request) {
        return ApiResponse.ok(incentiveService.upsertRule(request));
    }

    @GetMapping("/analytics/pipeline")
    public ApiResponse<PipelineAnalyticsResponse> pipelineAnalytics() {
        return ApiResponse.ok(analyticsService.pipeline());
    }

    @GetMapping("/analytics/summary")
    public ApiResponse<PipelineSummaryResponse> pipelineSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<UUID> assignedRepIds) {
        return ApiResponse.ok(analyticsService.pipelineSummary(from, to, assignedRepIds));
    }

    @GetMapping("/analytics/overview")
    public ApiResponse<PlatformOverviewResponse> platformOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<UUID> assignedRepIds) {
        return ApiResponse.ok(analyticsService.platformOverview(from, to, assignedRepIds));
    }

    @GetMapping("/analytics/growth")
    public ApiResponse<GrowthAnalyticsResponse> growthAnalytics() {
        return ApiResponse.ok(analyticsService.growth());
    }

    @GetMapping("/analytics/reps")
    public ApiResponse<List<RepPerformanceResponse>> repAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(repService.repPerformance(weekStart, from, to));
    }

    @GetMapping("/analytics/me")
    public ApiResponse<RepPerformanceResponse> myAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(repService.myPerformance(weekStart, from, to));
    }
}
