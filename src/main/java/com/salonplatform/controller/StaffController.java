package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.staff.CreateStaffRequest;
import com.salonplatform.dto.staff.StaffResponse;
import com.salonplatform.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @PostMapping
    public ApiResponse<StaffResponse> create(@Valid @RequestBody CreateStaffRequest request) {
        return ApiResponse.ok(staffService.create(request));
    }

    @GetMapping
    public ApiResponse<List<StaffResponse>> list(@RequestParam(required = false) UUID branchId) {
        if (branchId != null) {
            return ApiResponse.ok(staffService.listByBranch(branchId));
        }
        return ApiResponse.ok(staffService.listAll());
    }
}
