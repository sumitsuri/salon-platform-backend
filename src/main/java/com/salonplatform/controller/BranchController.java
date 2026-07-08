package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.branch.BranchResponse;
import com.salonplatform.dto.branch.BranchTargetPerformanceResponse;
import com.salonplatform.dto.branch.BranchTargetTrendsResponse;
import com.salonplatform.dto.branch.CreateBranchRequest;
import com.salonplatform.dto.branch.UpdateBranchRequest;
import com.salonplatform.service.BranchManagementService;
import com.salonplatform.service.BranchPerformanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchManagementService branchService;
    private final BranchPerformanceService branchPerformanceService;

    @PostMapping
    public ApiResponse<BranchResponse> create(@Valid @RequestBody CreateBranchRequest request) {
        return ApiResponse.ok(branchService.create(request));
    }

    @GetMapping
    public ApiResponse<List<BranchResponse>> list() {
        return ApiResponse.ok(branchService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<BranchResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(branchService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<BranchResponse> update(@PathVariable UUID id, @RequestBody UpdateBranchRequest request) {
        return ApiResponse.ok(branchService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deactivate(@PathVariable UUID id) {
        branchService.deactivate(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/performance/targets")
    public ApiResponse<BranchTargetPerformanceResponse> targetPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<UUID> branchIds) {
        return ApiResponse.ok(branchPerformanceService.getTargetPerformance(startDate, endDate, branchIds));
    }

    @GetMapping("/performance/trends")
    public ApiResponse<BranchTargetTrendsResponse> targetTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<UUID> branchIds) {
        return ApiResponse.ok(branchPerformanceService.getTargetTrends(startDate, endDate, branchIds));
    }
}
