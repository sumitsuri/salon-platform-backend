package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.branch.BranchResponse;
import com.salonplatform.dto.branch.CreateBranchRequest;
import com.salonplatform.dto.tenant.CreateTenantRequest;
import com.salonplatform.dto.tenant.TenantResponse;
import com.salonplatform.dto.user.CreatePlatformUserRequest;
import com.salonplatform.dto.user.PlatformUserResponse;
import com.salonplatform.service.PlatformManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformManagementService platformService;

    @PostMapping("/tenants")
    public ApiResponse<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return ApiResponse.ok(platformService.createTenant(request));
    }

    @GetMapping("/tenants")
    public ApiResponse<List<TenantResponse>> listTenants() {
        return ApiResponse.ok(platformService.listTenants());
    }

    @DeleteMapping("/tenants/{tenantId}")
    public ApiResponse<Void> deactivateTenant(@PathVariable UUID tenantId) {
        platformService.deactivateTenant(tenantId);
        return ApiResponse.ok("Tenant deactivated", null);
    }

    @GetMapping("/tenants/{tenantId}/branches")
    public ApiResponse<List<BranchResponse>> listBranches(@PathVariable UUID tenantId) {
        return ApiResponse.ok(platformService.listBranches(tenantId));
    }

    @PostMapping("/tenants/{tenantId}/branches")
    public ApiResponse<BranchResponse> createBranch(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateBranchRequest request) {
        return ApiResponse.ok(platformService.createBranch(tenantId, request));
    }

    @DeleteMapping("/tenants/{tenantId}/branches/{branchId}")
    public ApiResponse<Void> deactivateBranch(
            @PathVariable UUID tenantId,
            @PathVariable UUID branchId) {
        platformService.deactivateBranch(tenantId, branchId);
        return ApiResponse.ok("Branch deactivated", null);
    }

    @GetMapping("/tenants/{tenantId}/users")
    public ApiResponse<List<PlatformUserResponse>> listUsers(@PathVariable UUID tenantId) {
        return ApiResponse.ok(platformService.listUsers(tenantId));
    }

    @PostMapping("/tenants/{tenantId}/users")
    public ApiResponse<PlatformUserResponse> createUser(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreatePlatformUserRequest request) {
        return ApiResponse.ok(platformService.createUser(tenantId, request));
    }

    @DeleteMapping("/tenants/{tenantId}/users/{userId}")
    public ApiResponse<Void> deactivateUser(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId) {
        platformService.deactivateUser(tenantId, userId);
        return ApiResponse.ok("User deactivated", null);
    }
}
