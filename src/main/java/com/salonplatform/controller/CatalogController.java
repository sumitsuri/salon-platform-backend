package com.salonplatform.controller;

import com.salonplatform.domain.entity.SalonService;
import com.salonplatform.domain.entity.ServiceCategory;
import com.salonplatform.domain.entity.BranchService;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.catalog.*;
import com.salonplatform.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @PostMapping("/categories")
    public ApiResponse<ServiceCategory> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.ok(catalogService.createCategory(request));
    }

    @GetMapping("/categories")
    public ApiResponse<List<ServiceCategory>> listCategories() {
        return ApiResponse.ok(catalogService.listCategories());
    }

    @PostMapping("/services")
    public ApiResponse<SalonService> createService(@Valid @RequestBody CreateServiceRequest request) {
        return ApiResponse.ok(catalogService.createService(request));
    }

    @PostMapping("/branches/{branchId}/pricing")
    public ApiResponse<BranchService> setPricing(@PathVariable UUID branchId,
                                                  @Valid @RequestBody BranchPricingRequest request) {
        return ApiResponse.ok(catalogService.setBranchPricing(branchId, request));
    }

    @GetMapping("/branches/{branchId}/services")
    public ApiResponse<List<BranchServiceResponse>> branchServices(@PathVariable UUID branchId) {
        return ApiResponse.ok(catalogService.listBranchServices(branchId));
    }
}
