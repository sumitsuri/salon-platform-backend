package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.availability.BranchAvailabilityResponse;
import com.salonplatform.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    /**
     * Fresha-style staff day board: who is free/busy, blocks with from–to times,
     * free gaps, and rolling duration metrics for staffing decisions.
     */
    @GetMapping("/{branchId}/availability")
    public ApiResponse<BranchAvailabilityResponse> dayBoard(
            @PathVariable UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(availabilityService.getBranchDay(branchId, date));
    }
}
