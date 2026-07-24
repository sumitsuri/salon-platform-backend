package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.attendance.AttendanceIncidentResponse;
import com.salonplatform.dto.attendance.CreateAttendanceIncidentRequest;
import com.salonplatform.service.AttendanceIncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance/incidents")
@RequiredArgsConstructor
public class AttendanceIncidentController {

    private final AttendanceIncidentService incidentService;

    @PostMapping
    public ApiResponse<AttendanceIncidentResponse> create(@Valid @RequestBody CreateAttendanceIncidentRequest request) {
        return ApiResponse.ok(incidentService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<AttendanceIncidentResponse>> list(
            @RequestParam UUID staffId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(incidentService.listForStaff(staffId, page, size));
    }
}
