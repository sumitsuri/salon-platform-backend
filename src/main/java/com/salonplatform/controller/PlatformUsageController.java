package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.dto.platform.PlatformTenantUsageDetailResponse;
import com.salonplatform.dto.platform.PlatformUsageAnalyticsResponse;
import com.salonplatform.dto.platform.PlatformUsageHeatmapResponse;
import com.salonplatform.dto.platform.PlatformUsageInsights;
import com.salonplatform.dto.platform.PlatformUsagePortfolioResponse;
import com.salonplatform.dto.platform.PlatformTelemetryStatusResponse;
import com.salonplatform.dto.platform.PlatformUsageSummaryResponse;
import com.salonplatform.service.PlatformUsageAnalyticsService;
import com.salonplatform.service.PlatformUsageInsightsService;
import com.salonplatform.service.PlatformUsageService;
import com.salonplatform.telemetry.ProductTelemetryStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/usage")
@RequiredArgsConstructor
public class PlatformUsageController {

    private final PlatformUsageService platformUsageService;
    private final PlatformUsageAnalyticsService platformUsageAnalyticsService;
    private final ProductTelemetryStatusService telemetryStatusService;
    private final PlatformUsageInsightsService platformUsageInsightsService;

    @GetMapping("/telemetry-status")
    public ApiResponse<PlatformTelemetryStatusResponse> telemetryStatus() {
        return ApiResponse.ok(telemetryStatusService.status());
    }

    @GetMapping("/portfolio")
    public ApiResponse<PlatformUsagePortfolioResponse> portfolio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean includeDemo,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID branchId) {
        return ApiResponse.ok(platformUsageAnalyticsService.portfolio(from, to, includeDemo, tenantId, branchId));
    }

    @GetMapping("/heatmap")
    public ApiResponse<PlatformUsageHeatmapResponse> heatmap(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean includeDemo,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID branchId) {
        return ApiResponse.ok(
                platformUsageAnalyticsService.heatmap(from, to, includeDemo, tenantId, branchId));
    }

    @GetMapping("/tenants/{tenantId}/analytics")
    public ApiResponse<PlatformUsageAnalyticsResponse> tenantAnalytics(
            @PathVariable UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) String search) {
        return ApiResponse.ok(
                platformUsageAnalyticsService.tenantAnalytics(tenantId, from, to, branchId, userId, role, search));
    }

    /** Adoption insights across brands: health, change vs previous period, feature reach, trends. */
    @GetMapping("/insights/portfolio")
    public ApiResponse<PlatformUsageInsights.Portfolio> insightsPortfolio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean includeDemo) {
        return ApiResponse.ok(platformUsageInsightsService.portfolio(from, to, includeDemo));
    }

    /** One brand: feature adoption with last-used, branch scorecards, user activity, matrices and recent activity. */
    @GetMapping("/insights/tenants/{tenantId}")
    public ApiResponse<PlatformUsageInsights.Tenant> insightsTenant(
            @PathVariable UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID branchId) {
        return ApiResponse.ok(platformUsageInsightsService.tenant(tenantId, from, to, branchId));
    }

    @GetMapping("/summary")
    public ApiResponse<PlatformUsageSummaryResponse> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean includeDemo) {
        return ApiResponse.ok(platformUsageService.summary(from, to, includeDemo));
    }

    @GetMapping("/tenants/{tenantId}")
    public ApiResponse<PlatformTenantUsageDetailResponse> tenantDetail(
            @PathVariable UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(platformUsageService.tenantDetail(tenantId, from, to));
    }
}
