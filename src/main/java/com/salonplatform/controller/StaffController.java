package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.staff.*;
import com.salonplatform.dto.staffportal.StaffGoalResponse;
import com.salonplatform.dto.staffportal.StaffPerformanceReviewResponse;
import com.salonplatform.service.StaffAccountService;
import com.salonplatform.service.StaffGrowthManagementService;
import com.salonplatform.service.StaffPerformanceService;
import com.salonplatform.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;
    private final StaffPerformanceService staffPerformanceService;
    private final StaffAccountService staffAccountService;
    private final StaffGrowthManagementService staffGrowthManagementService;

    @PostMapping
    public ApiResponse<StaffResponse> create(@Valid @RequestBody CreateStaffRequest request) {
        return ApiResponse.ok(staffService.create(request));
    }

    @GetMapping
    public ApiResponse<List<StaffResponse>> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        if (branchId != null && !all) {
            return ApiResponse.ok(staffService.listByBranch(branchId));
        }
        return ApiResponse.ok(staffService.listAll(branchId));
    }

    @GetMapping("/{id}")
    public ApiResponse<StaffResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(staffService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<StaffResponse> update(@PathVariable UUID id, @RequestBody UpdateStaffRequest request) {
        return ApiResponse.ok(staffService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deactivate(@PathVariable UUID id) {
        staffService.deactivate(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/reactivate")
    public ApiResponse<StaffResponse> reactivate(@PathVariable UUID id) {
        return ApiResponse.ok(staffService.reactivate(id));
    }

    @PostMapping("/{id}/login")
    public ApiResponse<StaffResponse> provisionLogin(
            @PathVariable UUID id,
            @Valid @RequestBody ProvisionStaffLoginRequest request) {
        return ApiResponse.ok(staffAccountService.provisionLogin(id, request));
    }

    @GetMapping("/{id}/goals")
    public ApiResponse<List<StaffGoalResponse>> listGoals(@PathVariable UUID id) {
        return ApiResponse.ok(staffGrowthManagementService.listGoals(id));
    }

    @PostMapping("/{id}/goals")
    public ApiResponse<StaffGoalResponse> createGoal(
            @PathVariable UUID id,
            @Valid @RequestBody CreateStaffGoalRequest request) {
        return ApiResponse.ok(staffGrowthManagementService.createGoal(id, request));
    }

    @GetMapping("/{id}/reviews")
    public ApiResponse<List<StaffPerformanceReviewResponse>> listReviews(@PathVariable UUID id) {
        return ApiResponse.ok(staffGrowthManagementService.listReviews(id, false));
    }

    @PostMapping("/{id}/reviews")
    public ApiResponse<StaffPerformanceReviewResponse> createReview(
            @PathVariable UUID id,
            @Valid @RequestBody CreateStaffPerformanceReviewRequest request) {
        return ApiResponse.ok(staffGrowthManagementService.createReview(id, request));
    }

    @GetMapping("/performance/targets")
    public ApiResponse<StaffTargetPerformanceResponse> targetPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<UUID> branchIds) {
        return ApiResponse.ok(staffPerformanceService.getTargetPerformance(startDate, endDate, branchIds));
    }

    @GetMapping("/performance/trends")
    public ApiResponse<StaffTargetTrendsResponse> targetTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<UUID> branchIds) {
        return ApiResponse.ok(staffPerformanceService.getTargetTrends(startDate, endDate, branchIds));
    }
}
