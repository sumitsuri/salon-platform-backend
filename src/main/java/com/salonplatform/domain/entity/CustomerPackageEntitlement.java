package com.salonplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "customer_package_entitlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPackageEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false)
    private UUID serviceId;

    @Column(nullable = false, length = 200)
    private String serviceName;

    @Column(nullable = false)
    private Integer quantityTotal;

    @Column(nullable = false)
    private Integer quantityRemaining;
}
