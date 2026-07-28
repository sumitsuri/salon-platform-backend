package com.salonplatform.dto.booking;

import lombok.Data;

import java.util.UUID;

@Data
public class ApplyPromoRequest {
    private UUID couponId;
    private UUID offerId;
    /** When true, clears coupon and offer. */
    private Boolean clearPromo;
}
