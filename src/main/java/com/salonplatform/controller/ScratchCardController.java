package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.booking.BookingResponse;
import com.salonplatform.dto.scratch.IssueScratchCardRequest;
import com.salonplatform.dto.scratch.IssueScratchCardResponse;
import com.salonplatform.dto.scratch.RedeemScratchCardRequest;
import com.salonplatform.service.ScratchFootfallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scratch-cards")
@RequiredArgsConstructor
public class ScratchCardController {

    private final ScratchFootfallService scratchFootfallService;

    @GetMapping("/by-booking/{bookingId}")
    public ApiResponse<IssueScratchCardResponse> getByBooking(@PathVariable UUID bookingId) {
        return ApiResponse.ok(scratchFootfallService.findCardForBooking(bookingId));
    }

    @PostMapping("/issue")
    public ApiResponse<IssueScratchCardResponse> issue(@Valid @RequestBody IssueScratchCardRequest request) {
        return ApiResponse.ok(scratchFootfallService.issueCard(request));
    }

    @PostMapping("/redeem")
    public ApiResponse<BookingResponse> redeem(@Valid @RequestBody RedeemScratchCardRequest request) {
        return ApiResponse.ok(scratchFootfallService.redeemOnBooking(request));
    }
}
