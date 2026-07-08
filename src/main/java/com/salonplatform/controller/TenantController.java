package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.tenant.TenantResponse;
import com.salonplatform.dto.tenant.UpdateTenantRequest;
import com.salonplatform.service.TenantManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantManagementService tenantManagementService;

    @GetMapping
    public ApiResponse<TenantResponse> getOwn() {
        return ApiResponse.ok(tenantManagementService.getOwn());
    }

    @PutMapping
    public ApiResponse<TenantResponse> update(@RequestBody UpdateTenantRequest request) {
        return ApiResponse.ok(tenantManagementService.update(request));
    }
}
