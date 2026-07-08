package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.domain.enums.LeaveStatus;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.leave.CreateLeaveRequest;
import com.salonplatform.dto.leave.LeaveListFilter;
import com.salonplatform.dto.leave.LeaveResponse;
import com.salonplatform.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leave")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping
    public ApiResponse<LeaveResponse> create(@Valid @RequestBody CreateLeaveRequest request) {
        return ApiResponse.ok(leaveService.create(request));
    }

    @PostMapping("/{leaveId}/approve")
    public ApiResponse<LeaveResponse> approve(@PathVariable UUID leaveId) {
        return ApiResponse.ok(leaveService.approve(leaveId));
    }

    @PostMapping("/{leaveId}/reject")
    public ApiResponse<LeaveResponse> reject(@PathVariable UUID leaveId) {
        return ApiResponse.ok(leaveService.reject(leaveId));
    }

    @GetMapping
    public ApiResponse<PageResponse<LeaveResponse>> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String staff,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(leaveService.listPaged(LeaveListFilter.builder()
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
}
