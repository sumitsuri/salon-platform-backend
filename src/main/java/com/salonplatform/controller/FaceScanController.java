package com.salonplatform.controller;

import com.salonplatform.domain.enums.FaceCaptureZone;
import com.salonplatform.domain.enums.FaceLightMode;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.facescan.*;
import com.salonplatform.service.FaceScanPhotoStorageService;
import com.salonplatform.service.FaceScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/face-scans")
@RequiredArgsConstructor
public class FaceScanController {

    private final FaceScanService faceScanService;
    private final FaceScanPhotoStorageService photoStorage;

    @PostMapping
    public ApiResponse<FaceScanSessionResponse> create(@Valid @RequestBody CreateFaceScanRequest request) {
        return ApiResponse.ok(faceScanService.create(request));
    }

    @PostMapping(value = "/{sessionId}/captures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FaceScanCaptureDto> addCapture(
            @PathVariable UUID sessionId,
            @RequestParam("zone") FaceCaptureZone zone,
            @RequestParam(value = "lightMode", defaultValue = "WHITE") FaceLightMode lightMode,
            @RequestParam("photo") MultipartFile photo) {
        return ApiResponse.ok(faceScanService.addCapture(sessionId, zone, lightMode, photo));
    }

    @PostMapping("/{sessionId}/analyze")
    public ApiResponse<FaceScanSessionResponse> analyze(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) AnalyzeFaceScanRequest request) {
        return ApiResponse.ok(faceScanService.analyze(sessionId, request != null ? request : new AnalyzeFaceScanRequest()));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<FaceScanSessionResponse> get(@PathVariable UUID sessionId) {
        return ApiResponse.ok(faceScanService.get(sessionId));
    }

    @GetMapping
    public ApiResponse<PageResponse<FaceScanSessionResponse>> list(
            @RequestParam UUID branchId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(faceScanService.list(branchId, customerId, page, size));
    }

    @GetMapping("/captures/{captureId}/photo")
    public ResponseEntity<byte[]> capturePhoto(@PathVariable UUID captureId) {
        String key = faceScanService.photoKeyForCapture(captureId);
        byte[] bytes = photoStorage.load(key);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(MediaType.parseMediaType(photoStorage.contentTypeForKey(key)))
                .body(bytes);
    }
}
