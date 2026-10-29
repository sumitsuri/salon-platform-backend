package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.demand.DemandInsightsResponse;
import com.salonplatform.service.DemandInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Busy hours and staffing fit (brand admin). */
@RestController
@RequestMapping("/api/v1/analytics/demand")
@RequiredArgsConstructor
public class DemandInsightsController {

    private final DemandInsightsService service;

    /** All active branches, or one with {@code branchId}. Defaults to the last 90 days. */
    @GetMapping
    public ApiResponse<DemandInsightsResponse> insights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID branchId) {
        return ApiResponse.ok(service.getInsights(startDate, endDate, branchId));
    }
}
