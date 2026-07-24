package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.auth.AuthResponse;
import com.salonplatform.dto.user.UpdateLocaleRequest;
import com.salonplatform.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @PatchMapping("/locale")
    public ApiResponse<AuthResponse> updateLocale(@Valid @RequestBody UpdateLocaleRequest request) {
        return ApiResponse.ok(userProfileService.updateLocale(request));
    }
}
