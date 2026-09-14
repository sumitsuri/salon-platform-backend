package com.salonplatform.controller;

import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.packageplan.CreateServicePackagePlanRequest;
import com.salonplatform.dto.packageplan.CustomerPackageSubscriptionResponse;
import com.salonplatform.dto.packageplan.PackageExpiringFilter;
import com.salonplatform.dto.packageplan.ServicePackagePlanResponse;
import com.salonplatform.dto.packageplan.UpdateServicePackagePlanRequest;
import com.salonplatform.service.ServicePackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/packages")
@RequiredArgsConstructor
public class PackageController {

    private final ServicePackageService servicePackageService;

    @GetMapping("/plans")
    public ApiResponse<List<ServicePackagePlanResponse>> listPlans(
            @RequestParam(defaultValue = "false") boolean predefinedOnly) {
        return ApiResponse.ok(servicePackageService.listPlans(predefinedOnly));
    }

    @GetMapping("/plans/active")
    public ApiResponse<List<ServicePackagePlanResponse>> listActiveForBranch(@RequestParam UUID branchId) {
        return ApiResponse.ok(servicePackageService.listActiveForBranch(branchId));
    }

    @PostMapping("/plans")
    public ApiResponse<ServicePackagePlanResponse> createPlan(
            @Valid @RequestBody CreateServicePackagePlanRequest request) {
        return ApiResponse.ok(servicePackageService.createPlan(request));
    }

    @PutMapping("/plans/{id}")
    public ApiResponse<ServicePackagePlanResponse> updatePlan(
            @PathVariable UUID id, @Valid @RequestBody UpdateServicePackagePlanRequest request) {
        return ApiResponse.ok(servicePackageService.updatePlan(id, request));
    }

    @DeleteMapping("/plans/{id}")
    public ApiResponse<Void> deletePlan(@PathVariable UUID id) {
        servicePackageService.deletePlan(id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/plans/{id}/status")
    public ApiResponse<ServicePackagePlanResponse> planStatus(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(servicePackageService.updatePlanStatus(id, PromoStatus.valueOf(body.get("status"))));
    }

    @GetMapping("/customers/{customerId}/active")
    public ApiResponse<List<CustomerPackageSubscriptionResponse>> activeForCustomer(@PathVariable UUID customerId) {
        return ApiResponse.ok(servicePackageService.listActiveForCustomer(customerId));
    }

    @GetMapping("/subscriptions/expiring")
    public ApiResponse<PageResponse<CustomerPackageSubscriptionResponse>> expiring(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) Integer withinDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PackageExpiringFilter filter = PackageExpiringFilter.builder()
                .branchId(branchId)
                .withinDays(withinDays)
                .expiresFrom(expiresFrom)
                .expiresTo(expiresTo)
                .page(page)
                .size(size)
                .build();
        return ApiResponse.ok(servicePackageService.listExpiring(filter));
    }
}
