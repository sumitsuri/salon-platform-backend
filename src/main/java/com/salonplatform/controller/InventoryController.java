package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.inventory.*;
import com.salonplatform.service.InventoryAnalyticsService;
import com.salonplatform.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryAnalyticsService inventoryAnalyticsService;

    @GetMapping("/vendors")
    public ApiResponse<List<VendorResponse>> listVendors() {
        return ApiResponse.ok(inventoryService.listVendors());
    }

    @PostMapping("/vendors")
    public ApiResponse<VendorResponse> createVendor(@Valid @RequestBody CreateVendorRequest request) {
        return ApiResponse.ok(inventoryService.createVendor(request));
    }

    @PutMapping("/vendors/{id}")
    public ApiResponse<VendorResponse> updateVendor(@PathVariable UUID id, @RequestBody UpdateVendorRequest request) {
        return ApiResponse.ok(inventoryService.updateVendor(id, request));
    }

    @DeleteMapping("/vendors/{id}")
    public ApiResponse<Void> deactivateVendor(@PathVariable UUID id) {
        inventoryService.deactivateVendor(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/products")
    public ApiResponse<List<ProductResponse>> listProducts() {
        return ApiResponse.ok(inventoryService.listProducts());
    }

    @PostMapping("/products")
    public ApiResponse<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.ok(inventoryService.createProduct(request));
    }

    @PutMapping("/products/{id}")
    public ApiResponse<ProductResponse> updateProduct(@PathVariable UUID id, @RequestBody UpdateProductRequest request) {
        return ApiResponse.ok(inventoryService.updateProduct(id, request));
    }

    @DeleteMapping("/products/{id}")
    public ApiResponse<Void> deactivateProduct(@PathVariable UUID id) {
        inventoryService.deactivateProduct(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/stock")
    public ApiResponse<List<StockItemResponse>> listStock(@RequestParam(required = false) UUID branchId) {
        return ApiResponse.ok(inventoryService.listStock(branchId));
    }

    @GetMapping("/movements")
    public ApiResponse<List<MovementResponse>> listMovements(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.ok(inventoryService.listMovements(branchId, fromDate, toDate));
    }

    @PostMapping("/movements")
    public ApiResponse<MovementResponse> createMovement(@Valid @RequestBody CreateMovementRequest request) {
        return ApiResponse.ok(inventoryService.createMovement(request));
    }

    @PostMapping("/sync-product-cost")
    public ApiResponse<Void> syncProductCost(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expenseMonth) {
        inventoryService.syncAllBranchesForMonth(expenseMonth);
        return ApiResponse.ok(null);
    }

    @GetMapping("/analytics/overview")
    public ApiResponse<InventoryOverviewResponse> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            @RequestParam(required = false) List<UUID> branchIds) {
        return ApiResponse.ok(inventoryAnalyticsService.getOverview(month, branchIds));
    }

    @GetMapping("/analytics/trends")
    public ApiResponse<InventoryTrendsResponse> trends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endMonth,
            @RequestParam(required = false) Integer months,
            @RequestParam(required = false) List<UUID> branchIds) {
        return ApiResponse.ok(inventoryAnalyticsService.getTrends(endMonth, months, branchIds));
    }
}
