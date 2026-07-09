package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.ExpenditureCategory;
import com.salonplatform.domain.enums.MovementType;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.inventory.*;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ForbiddenException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final List<MovementType> COST_TYPES = List.of(
            MovementType.RESTOCK, MovementType.USAGE, MovementType.WASTAGE, MovementType.RETAIL_SALE);

    private final VendorRepository vendorRepository;
    private final InventoryProductRepository productRepository;
    private final BranchInventoryRepository stockRepository;
    private final InventoryMovementRepository movementRepository;
    private final BranchRepository branchRepository;
    private final BranchExpenditureRepository expenditureRepository;
    private final UserRepository userRepository;

    // --- Vendors (CEO only) ---

    public List<VendorResponse> listVendors() {
        assertInventoryRead();
        UUID tenantId = SecurityUtils.requireTenantId();
        return vendorRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .map(this::toVendorResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public VendorResponse createVendor(CreateVendorRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Vendor vendor = vendorRepository.save(Vendor.builder()
                .tenantId(tenantId)
                .name(request.getName().trim())
                .contactPhone(request.getContactPhone())
                .contactEmail(request.getContactEmail())
                .notes(request.getNotes())
                .active(true)
                .build());
        return toVendorResponse(vendor);
    }

    @Transactional
    public VendorResponse updateVendor(UUID id, UpdateVendorRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        Vendor vendor = requireVendor(SecurityUtils.requireTenantId(), id);
        if (request.getName() != null) vendor.setName(request.getName().trim());
        if (request.getContactPhone() != null) vendor.setContactPhone(request.getContactPhone());
        if (request.getContactEmail() != null) vendor.setContactEmail(request.getContactEmail());
        if (request.getNotes() != null) vendor.setNotes(request.getNotes());
        return toVendorResponse(vendorRepository.save(vendor));
    }

    @Transactional
    public void deactivateVendor(UUID id) {
        SecurityUtils.assertBrandAdminOrAbove();
        Vendor vendor = requireVendor(SecurityUtils.requireTenantId(), id);
        vendor.setActive(false);
        vendorRepository.save(vendor);
    }

    // --- Products (CEO only) ---

    public List<ProductResponse> listProducts() {
        assertInventoryRead();
        UUID tenantId = SecurityUtils.requireTenantId();
        return productRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .map(this::toProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        requireVendor(tenantId, request.getVendorId());
        if (request.getUnitCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Unit cost must be non-negative");
        }
        InventoryProduct product = productRepository.save(InventoryProduct.builder()
                .tenantId(tenantId)
                .vendorId(request.getVendorId())
                .name(request.getName().trim())
                .sku(request.getSku())
                .category(request.getCategory())
                .unit(request.getUnit())
                .unitCost(request.getUnitCost())
                .retailPrice(request.getRetailPrice())
                .reorderLevel(request.getReorderLevel())
                .active(true)
                .build());
        return toProductResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        InventoryProduct product = requireProduct(tenantId, id);
        if (request.getVendorId() != null) {
            requireVendor(tenantId, request.getVendorId());
            product.setVendorId(request.getVendorId());
        }
        if (request.getName() != null) product.setName(request.getName().trim());
        if (request.getSku() != null) product.setSku(request.getSku());
        if (request.getCategory() != null) product.setCategory(request.getCategory());
        if (request.getUnit() != null) product.setUnit(request.getUnit());
        if (request.getUnitCost() != null) product.setUnitCost(request.getUnitCost());
        if (request.getRetailPrice() != null) product.setRetailPrice(request.getRetailPrice());
        if (request.getReorderLevel() != null) product.setReorderLevel(request.getReorderLevel());
        return toProductResponse(productRepository.save(product));
    }

    @Transactional
    public void deactivateProduct(UUID id) {
        SecurityUtils.assertBrandAdminOrAbove();
        InventoryProduct product = requireProduct(SecurityUtils.requireTenantId(), id);
        product.setActive(false);
        productRepository.save(product);
    }

    // --- Stock ---

    public List<StockItemResponse> listStock(UUID branchId) {
        assertInventoryRead();
        UUID tenantId = SecurityUtils.requireTenantId();
        UUID effectiveBranch = resolveBranchFilter(branchId);

        List<BranchInventory> stockList;
        if (effectiveBranch != null) {
            stockList = stockRepository.findByTenantIdAndBranchId(tenantId, effectiveBranch);
        } else {
            stockList = stockRepository.findByTenantId(tenantId);
        }

        return stockList.stream().map(this::toStockResponse).filter(Objects::nonNull).collect(Collectors.toList());
    }

    // --- Movements ---

    public List<MovementResponse> listMovements(UUID branchId, LocalDate fromDate, LocalDate toDate) {
        assertInventoryRead();
        UUID tenantId = SecurityUtils.requireTenantId();
        UUID effectiveBranch = resolveBranchFilter(branchId);

        LocalDate from = fromDate != null ? fromDate : LocalDate.now().withDayOfMonth(1);
        LocalDate to = toDate != null ? toDate : LocalDate.now();

        List<InventoryMovement> movements;
        if (effectiveBranch != null) {
            movements = movementRepository.findByTenantIdAndBranchIdAndMovementDateBetweenOrderByMovementDateDescCreatedAtDesc(
                    tenantId, effectiveBranch, from, to);
        } else {
            movements = movementRepository.findByTenantIdAndMovementDateBetweenOrderByMovementDateDescCreatedAtDesc(
                    tenantId, from, to);
        }
        return movements.stream().map(this::toMovementResponse).collect(Collectors.toList());
    }

    @Transactional
    public MovementResponse createMovement(CreateMovementRequest request) {
        assertInventoryWrite(request.getBranchId());
        UUID tenantId = SecurityUtils.requireTenantId();
        UserPrincipal user = SecurityUtils.currentUser();

        Branch branch = requireBranch(tenantId, request.getBranchId());
        InventoryProduct product = requireProduct(tenantId, request.getProductId());

        if (request.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new BadRequestException("Quantity cannot be zero");
        }

        BigDecimal unitCost = request.getUnitCost() != null ? request.getUnitCost() : product.getUnitCost();
        BigDecimal qty = request.getQuantity().abs();
        BigDecimal stockDelta = stockDelta(request.getMovementType(), request.getQuantity());
        BigDecimal totalCost = qty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);

        BranchInventory stock = stockRepository
                .findByTenantIdAndBranchIdAndProductId(tenantId, branch.getId(), product.getId())
                .orElseGet(() -> stockRepository.save(BranchInventory.builder()
                        .tenantId(tenantId)
                        .branchId(branch.getId())
                        .productId(product.getId())
                        .quantity(BigDecimal.ZERO)
                        .build()));

        BigDecimal newQty = stock.getQuantity().add(stockDelta);
        if (newQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Insufficient stock for this movement");
        }
        stock.setQuantity(newQty);
        stockRepository.save(stock);

        InventoryMovement movement = movementRepository.save(InventoryMovement.builder()
                .tenantId(tenantId)
                .branchId(branch.getId())
                .productId(product.getId())
                .movementType(request.getMovementType())
                .quantity(qty)
                .unitCost(unitCost)
                .totalCost(totalCost)
                .movementDate(request.getMovementDate())
                .note(request.getNote())
                .recordedByUserId(user.getId())
                .build());

        syncProductCostForMonth(tenantId, branch.getId(), request.getMovementDate().withDayOfMonth(1));

        return toMovementResponse(movement);
    }

    @Transactional
    public void syncProductCostForMonth(UUID tenantId, UUID branchId, LocalDate expenseMonth) {
        LocalDate month = expenseMonth.withDayOfMonth(1);
        LocalDate monthEnd = month.withDayOfMonth(month.lengthOfMonth());

        BigDecimal total = movementRepository.sumCostByBranchAndMonth(
                tenantId, branchId, month, monthEnd, COST_TYPES);

        if (total == null) total = BigDecimal.ZERO;

        BranchExpenditure existing = expenditureRepository
                .findByTenantIdAndBranchIdAndCategoryAndExpenseMonthAndActiveTrue(
                        tenantId, branchId, ExpenditureCategory.PRODUCT_COST, month)
                .orElse(null);

        if (total.compareTo(BigDecimal.ZERO) == 0 && existing == null) {
            return;
        }

        if (existing != null) {
            existing.setAmount(total);
            existing.setDescription("Auto-synced from inventory movements");
            expenditureRepository.save(existing);
        } else {
            expenditureRepository.save(BranchExpenditure.builder()
                    .tenantId(tenantId)
                    .branchId(branchId)
                    .category(ExpenditureCategory.PRODUCT_COST)
                    .expenseMonth(month)
                    .amount(total)
                    .description("Auto-synced from inventory movements")
                    .active(true)
                    .build());
        }
    }

    @Transactional
    public void syncAllBranchesForMonth(LocalDate expenseMonth) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        LocalDate month = expenseMonth.withDayOfMonth(1);
        for (Branch branch : branchRepository.findByTenantId(tenantId)) {
            syncProductCostForMonth(tenantId, branch.getId(), month);
        }
    }

    // --- Helpers ---

    private BigDecimal stockDelta(MovementType type, BigDecimal quantity) {
        BigDecimal qty = quantity.abs();
        return switch (type) {
            case RESTOCK -> qty;
            case USAGE, WASTAGE, RETAIL_SALE -> qty.negate();
            case ADJUSTMENT -> quantity;
        };
    }

    private void assertInventoryRead() {
        UserRole role = SecurityUtils.currentUser().getRole();
        if (role == UserRole.PLATFORM_SUPER_ADMIN || role == UserRole.BRAND_ADMIN || SecurityUtils.isManagerRole()) {
            return;
        }
        throw new ForbiddenException("Inventory access denied");
    }

    private void assertInventoryWrite(UUID branchId) {
        UserRole role = SecurityUtils.currentUser().getRole();
        if (role == UserRole.PLATFORM_SUPER_ADMIN || role == UserRole.BRAND_ADMIN) {
            return;
        }
        if (SecurityUtils.isManagerRole()) {
            SecurityUtils.assertBranchAccess(branchId);
            return;
        }
        throw new ForbiddenException("Inventory write access denied");
    }

    private UUID resolveBranchFilter(UUID branchId) {
        UserPrincipal user = SecurityUtils.currentUser();
        if (SecurityUtils.isManagerRole()) {
            return user.getBranchId();
        }
        return branchId;
    }

    private Vendor requireVendor(UUID tenantId, UUID id) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));
        if (!vendor.getTenantId().equals(tenantId) || !vendor.isActive()) {
            throw new ResourceNotFoundException("Vendor not found");
        }
        return vendor;
    }

    private InventoryProduct requireProduct(UUID tenantId, UUID id) {
        InventoryProduct product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!product.getTenantId().equals(tenantId) || !product.isActive()) {
            throw new ResourceNotFoundException("Product not found");
        }
        return product;
    }

    private Branch requireBranch(UUID tenantId, UUID branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (!branch.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Branch not found");
        }
        return branch;
    }

    private VendorResponse toVendorResponse(Vendor v) {
        return VendorResponse.builder()
                .id(v.getId())
                .name(v.getName())
                .contactPhone(v.getContactPhone())
                .contactEmail(v.getContactEmail())
                .notes(v.getNotes())
                .createdAt(v.getCreatedAt())
                .build();
    }

    private ProductResponse toProductResponse(InventoryProduct p) {
        String vendorName = vendorRepository.findById(p.getVendorId()).map(Vendor::getName).orElse("—");
        return ProductResponse.builder()
                .id(p.getId())
                .vendorId(p.getVendorId())
                .vendorName(vendorName)
                .name(p.getName())
                .sku(p.getSku())
                .category(p.getCategory())
                .unit(p.getUnit())
                .unitCost(p.getUnitCost())
                .retailPrice(p.getRetailPrice())
                .reorderLevel(p.getReorderLevel())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private StockItemResponse toStockResponse(BranchInventory s) {
        InventoryProduct product = productRepository.findById(s.getProductId()).orElse(null);
        if (product == null || !product.isActive()) return null;

        String branchName = branchRepository.findById(s.getBranchId()).map(Branch::getName).orElse("—");
        String vendorName = vendorRepository.findById(product.getVendorId()).map(Vendor::getName).orElse("—");
        BigDecimal stockValue = s.getQuantity().multiply(product.getUnitCost()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal reorder = product.getReorderLevel() != null ? product.getReorderLevel() : BigDecimal.ZERO;
        boolean outOfStock = s.getQuantity().compareTo(BigDecimal.ZERO) <= 0;
        boolean lowStock = !outOfStock && reorder.compareTo(BigDecimal.ZERO) > 0
                && s.getQuantity().compareTo(reorder) <= 0;

        return StockItemResponse.builder()
                .id(s.getId())
                .branchId(s.getBranchId())
                .branchName(branchName)
                .productId(product.getId())
                .productName(product.getName())
                .sku(product.getSku())
                .category(product.getCategory())
                .unit(product.getUnit())
                .vendorId(product.getVendorId())
                .vendorName(vendorName)
                .quantity(s.getQuantity())
                .reorderLevel(reorder)
                .unitCost(product.getUnitCost())
                .stockValue(stockValue)
                .lowStock(lowStock)
                .outOfStock(outOfStock)
                .build();
    }

    private MovementResponse toMovementResponse(InventoryMovement m) {
        InventoryProduct product = productRepository.findById(m.getProductId()).orElse(null);
        String branchName = branchRepository.findById(m.getBranchId()).map(Branch::getName).orElse("—");
        String recordedBy = m.getRecordedByUserId() != null
                ? userRepository.findById(m.getRecordedByUserId()).map(User::getName).orElse(null)
                : null;
        String vendorName = product != null
                ? vendorRepository.findById(product.getVendorId()).map(Vendor::getName).orElse("—")
                : "—";

        return MovementResponse.builder()
                .id(m.getId())
                .branchId(m.getBranchId())
                .branchName(branchName)
                .productId(m.getProductId())
                .productName(product != null ? product.getName() : "—")
                .sku(product != null ? product.getSku() : null)
                .vendorId(product != null ? product.getVendorId() : null)
                .vendorName(vendorName)
                .movementType(m.getMovementType())
                .quantity(m.getQuantity())
                .unitCost(m.getUnitCost())
                .totalCost(m.getTotalCost())
                .movementDate(m.getMovementDate())
                .note(m.getNote())
                .recordedByName(recordedBy)
                .createdAt(m.getCreatedAt())
                .build();
    }
}
