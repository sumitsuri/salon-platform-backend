package com.salonplatform.controller;

import com.salonplatform.domain.enums.AdvanceStatus;
import com.salonplatform.domain.enums.InstallmentStatus;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.advance.*;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.service.SalaryAdvanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Owner (brand admin) side of salary advances. Employees use the staff-portal endpoints. */
@RestController
@RequestMapping("/api/v1/salary-advances")
@RequiredArgsConstructor
public class SalaryAdvanceController {

    private final SalaryAdvanceService salaryAdvanceService;

    @GetMapping
    public ApiResponse<PageResponse<AdvanceResponse>> list(
            @RequestParam(required = false) AdvanceStatus status,
            @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(salaryAdvanceService.list(status, staffId, branchId, page, size));
    }

    @GetMapping("/budget")
    public ApiResponse<AdvanceBudgetResponse> budget() {
        return ApiResponse.ok(salaryAdvanceService.budget());
    }

    @PutMapping("/policy")
    public ApiResponse<AdvancePolicyResponse> updatePolicy(@Valid @RequestBody AdvancePolicyRequest request) {
        return ApiResponse.ok(salaryAdvanceService.updatePolicy(request));
    }

    @GetMapping("/employees")
    public ApiResponse<List<EmployeeAdvanceSummary>> employees(@RequestParam(required = false) UUID branchId) {
        return ApiResponse.ok(salaryAdvanceService.employeeSummaries(branchId));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<AdvanceResponse> approve(
            @PathVariable UUID id, @Valid @RequestBody(required = false) DecideAdvanceRequest request) {
        return ApiResponse.ok(salaryAdvanceService.approve(id, request != null ? request : new DecideAdvanceRequest()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<AdvanceResponse> reject(
            @PathVariable UUID id, @Valid @RequestBody(required = false) DecideAdvanceRequest request) {
        return ApiResponse.ok(salaryAdvanceService.reject(id, request));
    }

    @GetMapping("/deductions")
    public ApiResponse<DeductionsResponse> deductions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return ApiResponse.ok(salaryAdvanceService.deductions(month));
    }

    @PostMapping("/deductions/deduct-month")
    public ApiResponse<DeductionsResponse> deductMonth(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return ApiResponse.ok(salaryAdvanceService.deductMonth(month));
    }

    @PostMapping("/installments/{installmentId}/deduct")
    public ApiResponse<AdvanceInstallmentResponse> deduct(@PathVariable UUID installmentId) {
        return ApiResponse.ok(salaryAdvanceService.settleInstallment(installmentId, InstallmentStatus.DEDUCTED, null));
    }

    @PostMapping("/installments/{installmentId}/waive")
    public ApiResponse<AdvanceInstallmentResponse> waive(
            @PathVariable UUID installmentId, @RequestParam(required = false) String note) {
        return ApiResponse.ok(salaryAdvanceService.settleInstallment(installmentId, InstallmentStatus.WAIVED, note));
    }
}
