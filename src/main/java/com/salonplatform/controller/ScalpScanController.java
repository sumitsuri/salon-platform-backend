package com.salonplatform.controller;

import com.salonplatform.domain.enums.ScalpCaptureZone;
import com.salonplatform.domain.enums.ScalpLightMode;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.scalpscan.*;
import com.salonplatform.service.ScalpScanPhotoStorageService;
import com.salonplatform.service.ScalpScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scalp-scans")
@RequiredArgsConstructor
public class ScalpScanController {

    private final ScalpScanService scalpScanService;
    private final ScalpScanPhotoStorageService photoStorage;

    @PostMapping
    public ApiResponse<ScalpScanSessionResponse> create(@Valid @RequestBody CreateScalpScanRequest request) {
        return ApiResponse.ok(scalpScanService.create(request));
    }

    @PostMapping(value = "/{sessionId}/captures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ScalpScanCaptureDto> addCapture(
            @PathVariable UUID sessionId,
            @RequestParam ScalpCaptureZone zone,
            @RequestParam(defaultValue = "WHITE") ScalpLightMode lightMode,
            @RequestPart("photo") MultipartFile photo) {
        return ApiResponse.ok(scalpScanService.addCapture(sessionId, zone, lightMode, photo));
    }

    @PostMapping("/{sessionId}/analyze")
    public ApiResponse<ScalpScanSessionResponse> analyze(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) AnalyzeScalpScanRequest request) {
        return ApiResponse.ok(scalpScanService.analyze(sessionId, request != null ? request : new AnalyzeScalpScanRequest()));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<ScalpScanSessionResponse> get(@PathVariable UUID sessionId) {
        return ApiResponse.ok(scalpScanService.get(sessionId));
    }

    @GetMapping
    public ApiResponse<PageResponse<ScalpScanSessionResponse>> list(
            @RequestParam UUID branchId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(scalpScanService.list(branchId, customerId, page, size));
    }

    @GetMapping("/captures/{captureId}/photo")
    public ResponseEntity<byte[]> capturePhoto(@PathVariable UUID captureId) {
        String key = scalpScanService.photoKeyForCapture(captureId);
        byte[] bytes = photoStorage.load(key);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(MediaType.parseMediaType(photoStorage.contentTypeForKey(key)))
                .body(bytes);
    }
}
