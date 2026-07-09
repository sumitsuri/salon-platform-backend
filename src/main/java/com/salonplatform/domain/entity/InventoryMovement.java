package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.MovementType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType movementType;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCost;

    @Column(nullable = false)
    private LocalDate movementDate;

    private String note;

    private UUID recordedByUserId;

    @CreationTimestamp
    private Instant createdAt;
}
