package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.scratch.CaptureScratchContactRequest;
import com.salonplatform.dto.scratch.PublicScratchCardResponse;
import com.salonplatform.service.ScratchFootfallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/scratch")
@RequiredArgsConstructor
public class PublicScratchController {

    private final ScratchFootfallService scratchFootfallService;

    @GetMapping("/{token}")
    public ApiResponse<PublicScratchCardResponse> get(@PathVariable String token) {
        return ApiResponse.ok(scratchFootfallService.getPublicCard(token));
    }

    @PostMapping("/{token}/scratch")
    public ApiResponse<PublicScratchCardResponse> scratch(@PathVariable String token) {
        return ApiResponse.ok(scratchFootfallService.scratchPublicCard(token));
    }

    @PostMapping("/{token}/contact")
    public ApiResponse<PublicScratchCardResponse> contact(
            @PathVariable String token, @Valid @RequestBody CaptureScratchContactRequest request) {
        return ApiResponse.ok(scratchFootfallService.captureContact(token, request));
    }
}
