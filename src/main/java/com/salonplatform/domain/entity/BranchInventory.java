package com.salonplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branch_inventory", uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false, precision = 19, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @UpdateTimestamp
    private Instant updatedAt;
}
