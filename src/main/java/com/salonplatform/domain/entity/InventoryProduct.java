package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.InventoryUnit;
import com.salonplatform.domain.enums.ProductCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private String name;

    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryUnit unit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitCost;

    @Column(precision = 19, scale = 2)
    private BigDecimal retailPrice;

    @Column(precision = 19, scale = 3)
    private BigDecimal reorderLevel;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
