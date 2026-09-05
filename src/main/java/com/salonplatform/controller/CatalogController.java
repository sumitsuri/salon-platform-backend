package com.salonplatform.controller;

import com.salonplatform.domain.entity.BranchService;
import com.salonplatform.domain.entity.SalonService;
import com.salonplatform.domain.entity.ServiceCategory;
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

    @PatchMapping("/categories/{categoryId}")
    public ApiResponse<ServiceCategory> updateCategory(@PathVariable UUID categoryId,
                                                       @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.ok(catalogService.updateCategory(categoryId, request));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable UUID categoryId) {
        catalogService.deactivateCategory(categoryId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/categories")
    public ApiResponse<List<ServiceCategory>> listCategories(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(catalogService.listCategories(includeInactive));
    }

    @PostMapping("/services")
    public ApiResponse<SalonService> createService(@Valid @RequestBody CreateServiceRequest request) {
        return ApiResponse.ok(catalogService.createService(request));
    }

    @GetMapping("/services")
    public ApiResponse<List<CatalogServiceResponse>> listServices(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(catalogService.listServices(includeInactive));
    }

    @GetMapping("/services/{serviceId}")
    public ApiResponse<CatalogServiceResponse> getService(@PathVariable UUID serviceId) {
        return ApiResponse.ok(catalogService.getService(serviceId));
    }

    @PatchMapping("/services/{serviceId}")
    public ApiResponse<SalonService> updateService(@PathVariable UUID serviceId,
                                                   @RequestBody UpdateServiceRequest request) {
        return ApiResponse.ok(catalogService.updateService(serviceId, request));
    }

    @DeleteMapping("/services/{serviceId}")
    public ApiResponse<Void> deleteService(@PathVariable UUID serviceId) {
        catalogService.deactivateService(serviceId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/services/{serviceId}/branches")
    public ApiResponse<CatalogServiceResponse> setServiceBranches(
            @PathVariable UUID serviceId,
            @Valid @RequestBody SetServiceBranchesRequest request) {
        return ApiResponse.ok(catalogService.setServiceBranches(serviceId, request));
    }

    @PostMapping("/branches/{branchId}/pricing")
    public ApiResponse<BranchService> setPricing(@PathVariable UUID branchId,
                                                  @Valid @RequestBody BranchPricingRequest request) {
        return ApiResponse.ok(catalogService.setBranchPricing(branchId, request));
    }

    @DeleteMapping("/branches/{branchId}/pricing/{serviceId}")
    public ApiResponse<Void> removePricing(@PathVariable UUID branchId, @PathVariable UUID serviceId) {
        catalogService.removeBranchPricing(branchId, serviceId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/branches/{branchId}/services")
    public ApiResponse<List<BranchServiceResponse>> branchServices(@PathVariable UUID branchId) {
        return ApiResponse.ok(catalogService.listBranchServices(branchId));
    }

    @PostMapping("/branches/{branchId}/bootstrap-services")
    public ApiResponse<Integer> bootstrapBranchServices(@PathVariable UUID branchId) {
        return ApiResponse.ok(catalogService.bootstrapBranchServices(branchId));
    }
}
