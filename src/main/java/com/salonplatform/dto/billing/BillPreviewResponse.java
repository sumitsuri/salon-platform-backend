package com.salonplatform.dto.billing;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BillPreviewResponse {
    private List<BillLinePreview> lines;
    private BigDecimal subtotal;
    private BigDecimal membershipDiscountAmount;
    private BigDecimal promoDiscountAmount;
    private BigDecimal discountAmount;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal grandTotal;
    private UUID couponId;
    private UUID offerId;
    private UUID membershipSubscriptionId;
    private String membershipLabel;
    private String promoLabel;
    /** Membership card fee included on this bill (exempt from GST). */
    private java.math.BigDecimal membershipFeeAmount;
    private String membershipFeeLabel;
    /** Manager FLAT/PERCENT discount when no coupon/offer is applied. */
    private BigDecimal manualDiscountAmount;
    private String manualDiscountLabel;
}
