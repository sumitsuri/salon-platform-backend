package com.salonplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "invoice_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private UUID bookingId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String invoiceNumber;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal membershipDiscountAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal promoDiscountAmount = BigDecimal.ZERO;

    private UUID couponId;

    private UUID offerId;

    private UUID membershipSubscriptionId;

    private String membershipLabel;

    /** Member card fee sold with this visit (exempt from GST). */
    @Column(precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal membershipFeeAmount = BigDecimal.ZERO;

    private String membershipFeeLabel;

    private String promoLabel;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cgstAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal sgstAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal;

    @Column(nullable = false)
    private String branchGstin;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerPhone;

    private String customerSociety;
    private String customerFlat;

    /** S3 object key or local filesystem path for the pre-generated PDF. */
    private String pdfStorageKey;

    private Instant pdfStoredAt;

    @CreationTimestamp
    private Instant issuedAt;
}
