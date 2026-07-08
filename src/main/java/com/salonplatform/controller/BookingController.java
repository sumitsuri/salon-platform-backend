package com.salonplatform.controller;

import com.salonplatform.domain.enums.BookingStatus;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.billing.BillPreviewResponse;
import com.salonplatform.dto.booking.BookingListFilter;
import com.salonplatform.dto.booking.BookingResponse;
import com.salonplatform.dto.booking.CreateBookingRequest;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.payment.RecordPaymentRequest;
import com.salonplatform.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ApiResponse<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        return ApiResponse.ok(bookingService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(bookingService.getById(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<BookingResponse>> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String stylist,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        BookingListFilter filter = BookingListFilter.builder()
                .branchId(branchId)
                .customer(customer)
                .branch(branch)
                .service(service)
                .stylist(stylist)
                .status(status)
                .minAmount(minAmount)
                .maxAmount(maxAmount)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .page(page)
                .size(size)
                .build();
        return ApiResponse.ok(bookingService.listPaged(filter));
    }

    @GetMapping("/{id}/bill-preview")
    public ApiResponse<BillPreviewResponse> billPreview(@PathVariable UUID id) {
        return ApiResponse.ok(bookingService.previewBill(id));
    }

    @PostMapping("/{id}/payments")
    public ApiResponse<BookingResponse> pay(@PathVariable UUID id, @Valid @RequestBody RecordPaymentRequest request) {
        return ApiResponse.ok(bookingService.completePayment(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable UUID id) {
        bookingService.cancel(id);
        return ApiResponse.ok("Booking cancelled", null);
    }
}
