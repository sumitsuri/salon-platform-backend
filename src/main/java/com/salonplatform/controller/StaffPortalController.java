package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.attendance.PunchResult;
import com.salonplatform.dto.attendance.VerifiedPunchRequest;
import com.salonplatform.dto.leave.CreateLeaveRequest;
import com.salonplatform.dto.leave.LeaveResponse;
import com.salonplatform.dto.staffportal.*;
import com.salonplatform.service.AttendancePhotoStorageService;
import com.salonplatform.service.StaffPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff-portal")
@RequiredArgsConstructor
public class StaffPortalController {

    private final StaffPortalService staffPortalService;
    private final AttendancePhotoStorageService photoStorage;

    @GetMapping("/me")
    public ApiResponse<StaffPortalProfileResponse> profile() {
        return ApiResponse.ok(staffPortalService.getProfile());
    }

    @PatchMapping("/me")
    public ApiResponse<StaffPortalProfileResponse> updateProfile(@RequestBody UpdateStaffPortalProfileRequest request) {
        return ApiResponse.ok(staffPortalService.updateProfile(request));
    }

    @PostMapping(value = "/me/profile-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StaffPortalProfileResponse> uploadProfilePhoto(@RequestPart("photo") MultipartFile photo) {
        return ApiResponse.ok(staffPortalService.uploadProfilePhoto(photo));
    }

    @PostMapping(value = "/me/aadhar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StaffPortalProfileResponse> uploadAadhar(@RequestPart("document") MultipartFile document) {
        return ApiResponse.ok(staffPortalService.uploadAadhar(document));
    }

    @GetMapping("/me/profile-photo")
    public ResponseEntity<byte[]> profilePhoto() {
        byte[] bytes = staffPortalService.loadProfilePhoto();
        String contentType = staffPortalService.profilePhotoContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }

    @GetMapping("/me/aadhar")
    public ResponseEntity<byte[]> aadharDocument() {
        byte[] bytes = staffPortalService.loadAadharDocument();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @PostMapping(value = "/attendance/punch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PunchResult> punch(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double accuracyMeters,
            @RequestParam(required = false) Boolean locationHighAccuracy,
            @RequestPart("photo") MultipartFile photo) {
        VerifiedPunchRequest request = new VerifiedPunchRequest();
        request.setAction(action);
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setAccuracyMeters(accuracyMeters);
        request.setLocationHighAccuracy(locationHighAccuracy);
        return ApiResponse.ok(staffPortalService.selfPunch(request, photo));
    }

    @GetMapping("/attendance/month")
    public ApiResponse<StaffAttendanceMonthResponse> attendanceMonth(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ApiResponse.ok(staffPortalService.attendanceMonth(year, month));
    }

    @GetMapping("/growth")
    public ApiResponse<StaffGrowthSnapshotResponse> growth() {
        return ApiResponse.ok(staffPortalService.growthSnapshot());
    }

    @GetMapping("/sales/insights")
    public ApiResponse<StaffPortalSalesInsightsResponse> salesInsights(
            @RequestParam(defaultValue = "2") int historyMonths) {
        return ApiResponse.ok(staffPortalService.salesInsights(historyMonths));
    }

    @GetMapping("/leaves")
    public ApiResponse<List<LeaveResponse>> leaves() {
        return ApiResponse.ok(staffPortalService.myLeaves());
    }

    @PostMapping("/leaves")
    public ApiResponse<LeaveResponse> applyLeave(@Valid @RequestBody CreateLeaveRequest request) {
        return ApiResponse.ok(staffPortalService.applyLeave(request));
    }

    @GetMapping("/goals")
    public ApiResponse<List<StaffGoalResponse>> goals() {
        return ApiResponse.ok(staffPortalService.myGoals());
    }

    @GetMapping("/reviews")
    public ApiResponse<List<StaffPerformanceReviewResponse>> reviews() {
        return ApiResponse.ok(staffPortalService.myReviews());
    }
}
