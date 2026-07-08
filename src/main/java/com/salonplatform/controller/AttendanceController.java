package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.attendance.*;
import com.salonplatform.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/biometric/punch")
    public ApiResponse<PunchResult> biometricPunch(@Valid @RequestBody BiometricPunchRequest request) {
        return ApiResponse.ok(attendanceService.biometricPunch(request));
    }

    @PostMapping("/manual")
    public ApiResponse<AttendanceResponse> manualEntry(@Valid @RequestBody ManualAttendanceRequest request) {
        return ApiResponse.ok(attendanceService.manualEntry(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<AttendanceResponse>> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String staff,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(attendanceService.listPaged(AttendanceListFilter.builder()
                .branchId(branchId)
                .staff(staff)
                .branch(branch)
                .status(status)
                .dateFrom(startDate)
                .dateTo(endDate)
                .page(page)
                .size(size)
                .build()));
    }

    @GetMapping("/today")
    public ApiResponse<List<AttendanceResponse>> today(@RequestParam UUID branchId) {
        return ApiResponse.ok(attendanceService.todayForBranch(branchId));
    }
}
