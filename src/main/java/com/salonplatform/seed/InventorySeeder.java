package com.salonplatform.seed;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.*;
import com.salonplatform.domain.repository.*;
import com.salonplatform.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class InventorySeeder implements CommandLineRunner {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final VendorRepository vendorRepository;
    private final InventoryProductRepository productRepository;
    private final BranchInventoryRepository stockRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryService inventoryService;

    @Override
    @Transactional
    public void run(String... args) {
        for (String slug : SeedCatalog.slugs()) {
            tenantRepository.findBySlug(slug).ifPresent(this::seedTenant);
        }
    }

    private void seedTenant(Tenant tenant) {
        List<Branch> branches = branchRepository.findByTenantId(tenant.getId());
        if (branches.isEmpty()) return;

        List<InventoryProduct> existingProducts =
                productRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenant.getId());
        final List<InventoryProduct> products;
        if (existingProducts.isEmpty()) {
            Vendor loreal = vendorRepository.save(Vendor.builder()
                    .tenantId(tenant.getId()).name("L'Oréal Professional").contactPhone("9800000001").active(true).build());
            Vendor wella = vendorRepository.save(Vendor.builder()
                    .tenantId(tenant.getId()).name("Wella Professionals").contactPhone("9800000002").active(true).build());
            Vendor local = vendorRepository.save(Vendor.builder()
                    .tenantId(tenant.getId()).name("Bangalore Beauty Supply").contactPhone("9800000003").active(true).build());

            InventoryProduct shampoo = saveProduct(tenant.getId(), loreal.getId(), "L'Oréal Serie Expert Shampoo 1.5L",
                    "LOR-SHP-15", ProductCategory.CONSUMABLE, InventoryUnit.BOTTLE, "1850", "2", null);
            InventoryProduct developer = saveProduct(tenant.getId(), wella.getId(), "Wella Color Touch 6% Developer 1L",
                    "WEL-DEV-6", ProductCategory.CONSUMABLE, InventoryUnit.BOTTLE, "720", "3", null);
            InventoryProduct colorTube = saveProduct(tenant.getId(), wella.getId(), "Wella Koleston Perfect 60ml",
                    "WEL-COL-60", ProductCategory.CONSUMABLE, InventoryUnit.PCS, "285", "20", null);
            InventoryProduct gloves = saveProduct(tenant.getId(), local.getId(), "Nitrile Gloves Box (100 pcs)",
                    "LOC-GLV-100", ProductCategory.CONSUMABLE, InventoryUnit.PCS, "450", "2", null);
            InventoryProduct serum = saveProduct(tenant.getId(), loreal.getId(), "L'Oréal Absolut Repair Serum 100ml",
                    "LOR-SRM-100", ProductCategory.RETAIL, InventoryUnit.BOTTLE, "980", "5", "1490");
            products = List.of(shampoo, developer, colorTube, gloves, serum);
        } else {
            products = existingProducts;
        }

        LocalDate today = LocalDate.now(ZONE);
        LocalDate thisMonth = today.withDayOfMonth(1);
        LocalDate lastMonth = thisMonth.minusMonths(1);
        int seededBranches = 0;

        for (Branch branch : branches) {
            if (!stockRepository.findByTenantIdAndBranchId(tenant.getId(), branch.getId()).isEmpty()) {
                continue;
            }
            seedBranchInventory(tenant.getId(), branch, products, lastMonth, thisMonth);
            seededBranches++;
        }

        if (seededBranches > 0 || existingProducts.isEmpty()) {
            log.info("Seeded inventory for '{}': {} products, {} new branch(es)",
                    tenant.getSlug(), products.size(),
                    seededBranches > 0 ? seededBranches : branches.size());
        }
    }

    private void seedBranchInventory(UUID tenantId, Branch branch, List<InventoryProduct> products,
                                     LocalDate lastMonth, LocalDate thisMonth) {
        boolean flagship = branch.getCode().equals("LIT") || branch.getCode().equals("IND");
        InventoryProduct shampoo = products.stream().filter(p -> "LOR-SHP-15".equals(p.getSku())).findFirst().orElse(null);
        InventoryProduct developer = products.stream().filter(p -> "WEL-DEV-6".equals(p.getSku())).findFirst().orElse(null);
        InventoryProduct colorTube = products.stream().filter(p -> "WEL-COL-60".equals(p.getSku())).findFirst().orElse(null);
        InventoryProduct gloves = products.stream().filter(p -> "LOC-GLV-100".equals(p.getSku())).findFirst().orElse(null);
        InventoryProduct serum = products.stream().filter(p -> "LOR-SRM-100".equals(p.getSku())).findFirst().orElse(null);
        if (shampoo == null || developer == null || colorTube == null || gloves == null || serum == null) {
            return;
        }

        for (InventoryProduct p : products) {
            stockRepository.save(BranchInventory.builder()
                    .tenantId(tenantId).branchId(branch.getId()).productId(p.getId())
                    .quantity(BigDecimal.ZERO).build());
        }

        recordMovement(tenantId, branch.getId(), shampoo, MovementType.RESTOCK,
                flagship ? "8" : "6", lastMonth.plusDays(1));
        recordMovement(tenantId, branch.getId(), developer, MovementType.RESTOCK,
                flagship ? "10" : "8", lastMonth.plusDays(1));
        recordMovement(tenantId, branch.getId(), colorTube, MovementType.RESTOCK,
                flagship ? "40" : "30", lastMonth.plusDays(1));
        recordMovement(tenantId, branch.getId(), gloves, MovementType.RESTOCK,
                flagship ? "4" : "3", lastMonth.plusDays(1));
        recordMovement(tenantId, branch.getId(), serum, MovementType.RESTOCK,
                flagship ? "8" : "5", lastMonth.plusDays(1));

        recordMovement(tenantId, branch.getId(), colorTube, MovementType.USAGE,
                flagship ? "15" : "12", lastMonth.plusDays(10));
        recordMovement(tenantId, branch.getId(), gloves, MovementType.USAGE,
                "1", lastMonth.plusDays(15));
        recordMovement(tenantId, branch.getId(), serum, MovementType.RETAIL_SALE,
                flagship ? "2" : "1", lastMonth.plusDays(20));

        recordMovement(tenantId, branch.getId(), shampoo, MovementType.USAGE,
                flagship ? "2" : "1.5", thisMonth.plusDays(3));
        recordMovement(tenantId, branch.getId(), colorTube, MovementType.USAGE,
                flagship ? "18" : "14", thisMonth.plusDays(5));
        recordMovement(tenantId, branch.getId(), developer, MovementType.WASTAGE,
                flagship ? "0.5" : "0.3", thisMonth.plusDays(7));

        inventoryService.syncProductCostForMonth(tenantId, branch.getId(), lastMonth);
        inventoryService.syncProductCostForMonth(tenantId, branch.getId(), thisMonth);
    }

    private InventoryProduct saveProduct(UUID tenantId, UUID vendorId, String name, String sku,
                                         ProductCategory cat, InventoryUnit unit, String cost,
                                         String reorder, String retail) {
        InventoryProduct p = InventoryProduct.builder()
                .tenantId(tenantId).vendorId(vendorId).name(name).sku(sku).category(cat).unit(unit)
                .unitCost(new BigDecimal(cost)).reorderLevel(new BigDecimal(reorder)).active(true).build();
        if (retail != null) p.setRetailPrice(new BigDecimal(retail));
        return productRepository.save(p);
    }

    private void recordMovement(UUID tenantId, UUID branchId, InventoryProduct product, MovementType type,
                                String qty, LocalDate date) {
        BigDecimal quantity = new BigDecimal(qty);
        BigDecimal unitCost = product.getUnitCost();
        BigDecimal totalCost = quantity.multiply(unitCost);

        BranchInventory stock = stockRepository
                .findByTenantIdAndBranchIdAndProductId(tenantId, branchId, product.getId()).orElseThrow();
        BigDecimal delta = switch (type) {
            case RESTOCK -> quantity;
            case USAGE, WASTAGE, RETAIL_SALE -> quantity.negate();
            default -> quantity;
        };
        stock.setQuantity(stock.getQuantity().add(delta));
        stockRepository.save(stock);

        movementRepository.save(InventoryMovement.builder()
                .tenantId(tenantId).branchId(branchId).productId(product.getId())
                .movementType(type).quantity(quantity.abs()).unitCost(unitCost).totalCost(totalCost)
                .movementDate(date).note("Demo seed").build());
    }
}
