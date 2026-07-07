package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.DiscountType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "booking_line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID bookingId;

    @Column(nullable = false)
    private UUID branchServiceId;

    @Column(nullable = false)
    private UUID serviceId;

    @Column(nullable = false)
    private UUID staffId;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Builder.Default
    private Integer quantity = 1;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate;

    @Enumerated(EnumType.STRING)
    private DiscountType lineDiscountType;

    @Column(precision = 12, scale = 2)
    private BigDecimal lineDiscountValue;

    private String lineDiscountNote;
}
