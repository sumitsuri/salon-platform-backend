package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.analytics.AttendanceDashboardResponse;
import com.salonplatform.dto.analytics.DashboardResponse;
import com.salonplatform.dto.analytics.RecommendationsResponse;
import com.salonplatform.dto.analytics.ServiceContributionResponse;
import com.salonplatform.dto.analytics.PlSummaryResponse;
import com.salonplatform.dto.analytics.PlTrendsResponse;
import com.salonplatform.service.AnalyticsService;
import com.salonplatform.service.AttendanceAnalyticsService;
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
}
