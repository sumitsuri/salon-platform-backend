package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.platform.PlatformTenantUsageDetailResponse;
import com.salonplatform.dto.platform.PlatformUsageSummaryResponse;
import com.salonplatform.service.PlatformUsageService;
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
