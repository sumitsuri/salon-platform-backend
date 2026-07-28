package com.salonplatform.dto.invoice;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class InvoiceDetailResponse {
    private UUID id;
    private UUID bookingId;
    private String invoiceNumber;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal membershipDiscountAmount;
    private BigDecimal promoDiscountAmount;
    private String membershipLabel;
    private String promoLabel;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal grandTotal;
    private String customerName;
    private String customerPhone;
    private Instant issuedAt;
    private boolean pdfAvailable;
    private Instant pdfStoredAt;
}
