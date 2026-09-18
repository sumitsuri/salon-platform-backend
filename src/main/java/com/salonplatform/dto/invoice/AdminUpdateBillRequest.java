package com.salonplatform.dto.invoice;

import com.salonplatform.dto.booking.BookingLineRequest;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class AdminUpdateBillRequest {
    @Valid
    private List<BookingLineRequest> lines;

    /** Optional note stored in audit log. */
    private String reason;
}
