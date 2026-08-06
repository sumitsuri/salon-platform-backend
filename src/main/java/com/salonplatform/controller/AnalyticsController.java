package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.analytics.*;
import com.salonplatform.service.AnalyticsService;
import com.salonplatform.service.AttendanceAnalyticsService;
import com.salonplatform.service.BenchmarkService;
import com.salonplatform.service.LocalSpotlightService;
import com.salonplatform.service.PlAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AttendanceAnalyticsService attendanceAnalyticsService;
    private final PlAnalyticsService plAnalyticsService;
    private final BenchmarkService benchmarkService;
    private final LocalSpotlightService localSpotlightService;

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) List<java.util.UUID> branchIds) {
        if (date != null && startDate == null && endDate == null) {
            startDate = date;
            endDate = date;
        }
        return ApiResponse.ok(analyticsService.getDashboard(startDate, endDate, branchIds));
    }

    @GetMapping("/recommendations")
    public ApiResponse<RecommendationsResponse> recommendations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) List<java.util.UUID> branchIds) {
        if (date != null && startDate == null && endDate == null) {
            startDate = date;
            endDate = date;
        }
        return ApiResponse.ok(analyticsService.getRecommendations(startDate, endDate, branchIds));
    }

    @GetMapping("/services")
    public ApiResponse<ServiceContributionResponse> services(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) List<java.util.UUID> branchIds,
            @RequestParam(required = false) String serviceName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (date != null && startDate == null && endDate == null) {
            startDate = date;
            endDate = date;
        }
        return ApiResponse.ok(analyticsService.getServiceContribution(startDate, endDate, branchIds, serviceName, page, size));
    }

    @GetMapping("/attendance")
    public ApiResponse<AttendanceDashboardResponse> attendanceDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<java.util.UUID> branchIds) {
        return ApiResponse.ok(attendanceAnalyticsService.getDashboard(startDate, endDate, branchIds));
    }

    @GetMapping("/pl")
    public ApiResponse<PlSummaryResponse> plSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<java.util.UUID> branchIds) {
        return ApiResponse.ok(plAnalyticsService.getPlSummary(startDate, endDate, branchIds));
    }

    @GetMapping("/pl/trends")
    public ApiResponse<PlTrendsResponse> plTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endMonth,
            @RequestParam(required = false) Integer months,
            @RequestParam(required = false) List<java.util.UUID> branchIds) {
        return ApiResponse.ok(plAnalyticsService.getPlTrends(endMonth, months, branchIds));
    }

    @GetMapping("/benchmark")
    public ApiResponse<BenchmarkResponse> benchmark(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<java.util.UUID> branchIds) {
        return ApiResponse.ok(benchmarkService.getBenchmark(startDate, endDate, branchIds));
    }

    @GetMapping("/benchmark/settings")
    public ApiResponse<BenchmarkSettingsResponse> benchmarkSettings() {
        return ApiResponse.ok(benchmarkService.getSettings());
    }

    @PatchMapping("/benchmark/settings")
    public ApiResponse<BenchmarkSettingsResponse> updateBenchmarkSettings(
            @RequestBody UpdateBenchmarkSettingsRequest request) {
        return ApiResponse.ok(benchmarkService.updateSettings(request));
    }

    @GetMapping("/benchmark/local-competitors")
    public ApiResponse<List<BenchmarkResponse.LocalCompetitorRow>> localCompetitors() {
        return ApiResponse.ok(benchmarkService.listLocalCompetitors());
    }

    @PostMapping("/benchmark/local-competitors")
    public ApiResponse<BenchmarkResponse.LocalCompetitorRow> createLocalCompetitor(
            @RequestBody UpsertLocalCompetitorRequest request) {
        return ApiResponse.ok(benchmarkService.createLocalCompetitor(request));
    }

    @PutMapping("/benchmark/local-competitors/{id}")
    public ApiResponse<BenchmarkResponse.LocalCompetitorRow> updateLocalCompetitor(
            @PathVariable java.util.UUID id,
            @RequestBody UpsertLocalCompetitorRequest request) {
        return ApiResponse.ok(benchmarkService.updateLocalCompetitor(id, request));
    }

    @DeleteMapping("/benchmark/local-competitors/{id}")
    public ApiResponse<Void> deleteLocalCompetitor(@PathVariable java.util.UUID id) {
        benchmarkService.deleteLocalCompetitor(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/local-spotlight")
    public ApiResponse<LocalSpotlightResponse> localSpotlight(
            @RequestParam(required = false) List<java.util.UUID> branchIds,
            @RequestParam(defaultValue = "2") int radiusKm,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.ok(localSpotlightService.getLocalSpotlight(branchIds, radiusKm, refresh));
    }

    @PostMapping("/local-spotlight/sync")
    public ApiResponse<LocalSpotlightSyncResponse> syncLocalSpotlight(
            @RequestParam(defaultValue = "2") int radiusKm,
            @RequestParam(defaultValue = "true") boolean force) {
        return ApiResponse.ok(localSpotlightService.syncFromGoogle(radiusKm, force));
    }
}
