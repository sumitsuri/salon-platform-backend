package com.salonplatform.dto.booking;

import com.salonplatform.domain.enums.DiscountType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreateBookingRequest {
    @NotNull
    private UUID branchId;
    @NotNull
    private UUID customerId;
    private String notes;
    @NotEmpty
    private List<BookingLineRequest> lines;
    private DiscountType billDiscountType;
    private BigDecimal billDiscountValue;
    private String billDiscountNote;
    private UUID couponId;
    private UUID offerId;
    /**
     * When true, booking stays {@code IN_PROGRESS} (open visit — add/change services later).
     * When false/null, booking is marked {@code READY_FOR_BILLING} for immediate payment.
     */
    private Boolean keepOpen;
}
