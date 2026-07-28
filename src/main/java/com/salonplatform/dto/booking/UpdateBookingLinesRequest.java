package com.salonplatform.dto.booking;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpdateBookingLinesRequest {
    @NotEmpty
    private List<BookingLineRequest> lines;
}
