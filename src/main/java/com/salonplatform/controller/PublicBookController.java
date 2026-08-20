package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.publicbook.PublicBookCreateAppointmentRequest;
import com.salonplatform.dto.publicbook.PublicBookModels;
import com.salonplatform.dto.publicbook.PublicBookSendOtpRequest;
import com.salonplatform.service.OnlineBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/book")
@RequiredArgsConstructor
public class PublicBookController {

    private final OnlineBookingService onlineBookingService;

    @GetMapping("/{tenantSlug}")
    public ApiResponse<PublicBookModels.TenantBranchesResponse> listBranches(@PathVariable String tenantSlug) {
        return ApiResponse.ok(onlineBookingService.listTenantBranches(tenantSlug));
    }

    @GetMapping("/{tenantSlug}/{branchCode}")
    public ApiResponse<PublicBookModels.ContextResponse> getContext(
            @PathVariable String tenantSlug,
            @PathVariable String branchCode) {
        return ApiResponse.ok(onlineBookingService.getBranchContext(tenantSlug, branchCode));
    }

    @GetMapping("/{tenantSlug}/{branchCode}/services")
    public ApiResponse<List<PublicBookModels.ServiceResponse>> listServices(
            @PathVariable String tenantSlug,
            @PathVariable String branchCode) {
        return ApiResponse.ok(onlineBookingService.listServices(tenantSlug, branchCode));
    }

    @GetMapping("/{tenantSlug}/{branchCode}/staff")
    public ApiResponse<List<PublicBookModels.StaffResponse>> listStaff(
            @PathVariable String tenantSlug,
            @PathVariable String branchCode) {
        return ApiResponse.ok(onlineBookingService.listStaff(tenantSlug, branchCode));
    }

    @GetMapping("/{tenantSlug}/{branchCode}/slots")
    public ApiResponse<List<PublicBookModels.SlotResponse>> listSlots(
            @PathVariable String tenantSlug,
            @PathVariable String branchCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) List<UUID> branchServiceIds,
            @RequestParam(required = false) UUID branchServiceId,
            @RequestParam(required = false) UUID staffId) {
        return ApiResponse.ok(onlineBookingService.listSlots(
                tenantSlug, branchCode, date, resolveServiceIds(branchServiceIds, branchServiceId), staffId));
    }

    private static List<UUID> resolveServiceIds(List<UUID> branchServiceIds, UUID branchServiceId) {
        if (branchServiceIds != null && !branchServiceIds.isEmpty()) {
            return branchServiceIds;
        }
        if (branchServiceId != null) {
            return List.of(branchServiceId);
        }
        return List.of();
    }

    @PostMapping("/{tenantSlug}/{branchCode}/otp/send")
    public ApiResponse<PublicBookModels.OtpResponse> sendOtp(
            @PathVariable String tenantSlug,
            @PathVariable String branchCode,
            @RequestBody PublicBookSendOtpRequest request) {
        return ApiResponse.ok(onlineBookingService.sendOtp(tenantSlug, branchCode, request.getPhone()));
    }

    @PostMapping("/{tenantSlug}/{branchCode}/appointments")
    public ApiResponse<PublicBookModels.AppointmentResponse> createAppointment(
            @PathVariable String tenantSlug,
            @PathVariable String branchCode,
            @Valid @RequestBody PublicBookCreateAppointmentRequest request) {
        return ApiResponse.ok(onlineBookingService.createAppointment(tenantSlug, branchCode, request));
    }
}
