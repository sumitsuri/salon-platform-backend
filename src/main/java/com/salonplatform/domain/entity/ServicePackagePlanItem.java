package com.salonplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "service_package_plan_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePackagePlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID planId;

    @Column(nullable = false)
    private UUID serviceId;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
