package com.salonplatform.dto.booking;

import com.salonplatform.domain.enums.DiscountType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class CreateBookingRequest {
    @NotNull
    private UUID branchId;
    @NotNull
    private UUID customerId;
    private String notes;
    /** May be empty when selling a membership or package on this visit (validated in service). */
    private List<BookingLineRequest> lines = new ArrayList<>();
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
    /** Optional membership plan to bill and activate on payment. */
    private UUID pendingMembershipPlanId;
    private UUID pendingPackagePlanId;
    private UUID pendingPackageSoldByStaffId;
}
