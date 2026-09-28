package com.salonplatform.dto.booking;

import com.salonplatform.domain.enums.DiscountType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
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
    /** May be empty when selling a membership or package on this visit, or booking a future
     *  appointment with services not yet chosen (validated in service). */
    private List<BookingLineRequest> lines = new ArrayList<>();
    private DiscountType billDiscountType;
    private BigDecimal billDiscountValue;
    private String billDiscountNote;
    private UUID couponId;
    private UUID offerId;
    /**
     * When true, booking stays {@code IN_PROGRESS} (open visit — add/change services later).
     * When false/null, booking is marked {@code READY_FOR_BILLING} for immediate payment.
     * Ignored when {@link #scheduledStartAt} is set.
     */
    private Boolean keepOpen;
    /** Optional membership plan to bill and activate on payment. */
    private UUID pendingMembershipPlanId;
    private UUID pendingPackagePlanId;
    private UUID pendingPackageSoldByStaffId;
    /**
     * When set, this creates a future appointment ({@code CONFIRMED}, not started) instead of an
     * immediate walk-in — mirrors how online bookings are scheduled. Requires {@link #staffId}
     * when {@link #lines} is empty, since a service-less appointment has no other way to be
     * attributed to a stylist column on the floor schedule.
     */
    private Instant scheduledStartAt;
    private Instant scheduledEndAt;
    /** Stylist for a future appointment whose services aren't chosen yet. */
    private UUID staffId;
}
