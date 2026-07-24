package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.attendance.*;
import com.salonplatform.service.AttendancePhotoStorageService;
import com.salonplatform.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendancePhotoStorageService photoStorage;

    @PostMapping("/biometric/punch")
    public ApiResponse<PunchResult> biometricPunch(@Valid @RequestBody BiometricPunchRequest request) {
        return ApiResponse.ok(attendanceService.biometricPunch(request));
    }

    @PostMapping(value = "/verified/punch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PunchResult> verifiedPunch(
            @RequestParam UUID staffId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double accuracyMeters,
            @RequestPart("photo") MultipartFile photo) {
        VerifiedPunchRequest request = new VerifiedPunchRequest();
        request.setStaffId(staffId);
        request.setAction(action);
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setAccuracyMeters(accuracyMeters);
        return ApiResponse.ok(attendanceService.verifiedPunch(request, photo));
    }

    @PostMapping("/manual")
    public ApiResponse<AttendanceResponse> manualEntry(@Valid @RequestBody ManualAttendanceRequest request) {
        return ApiResponse.ok(attendanceService.manualEntry(request));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "entry") String type) {
        var record = attendanceService.requireRecord(id);
        String key = "exit".equalsIgnoreCase(type) ? record.getExitPhotoKey() : record.getEntryPhotoKey();
        if (key == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = photoStorage.load(key);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(MediaType.parseMediaType(photoStorage.contentTypeForKey(key)))
                .body(bytes);
    }

    @GetMapping
    public ApiResponse<PageResponse<AttendanceResponse>> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) String staff,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(attendanceService.listPaged(AttendanceListFilter.builder()
                .branchId(branchId)
                .staffId(staffId)
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

    /** CEO-only: wipe all attendance records and photos for the tenant (testing / fresh start). */
    @DeleteMapping("/reset")
    public ApiResponse<java.util.Map<String, Integer>> resetAll() {
        int deleted = attendanceService.resetAllForTenant();
        return ApiResponse.ok(java.util.Map.of("deletedRecords", deleted));
    }
}
