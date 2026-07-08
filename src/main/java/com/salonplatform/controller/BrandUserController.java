package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.user.CreatePlatformUserRequest;
import com.salonplatform.dto.user.PlatformUserResponse;
import com.salonplatform.dto.user.UpdatePlatformUserRequest;
import com.salonplatform.service.BrandUserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class BrandUserController {

    private final BrandUserManagementService brandUserManagementService;

    @GetMapping
    public ApiResponse<List<PlatformUserResponse>> list() {
        return ApiResponse.ok(brandUserManagementService.list());
    }

    @PostMapping
    public ApiResponse<PlatformUserResponse> create(@Valid @RequestBody CreatePlatformUserRequest request) {
        return ApiResponse.ok(brandUserManagementService.create(request));
    }

    @PutMapping("/{userId}")
    public ApiResponse<PlatformUserResponse> update(
            @PathVariable UUID userId, @RequestBody UpdatePlatformUserRequest request) {
        return ApiResponse.ok(brandUserManagementService.update(userId, request));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deactivate(@PathVariable UUID userId) {
        brandUserManagementService.deactivate(userId);
        return ApiResponse.ok(null);
    }
}
