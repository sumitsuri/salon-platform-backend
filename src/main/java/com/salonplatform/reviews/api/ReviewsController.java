package com.salonplatform.reviews.api;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.reviews.application.GuestVoiceAnalyticsService;
import com.salonplatform.reviews.application.ReviewInvitationService;
import com.salonplatform.reviews.dto.GuestVoiceSummaryDto;
import com.salonplatform.reviews.dto.ReviewInvitationDto;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewsController {

    private final ReviewInvitationService reviewInvitationService;
    private final GuestVoiceAnalyticsService guestVoiceAnalyticsService;

    @GetMapping("/invitations/by-visit/{visitId}")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'SALON_MANAGER', 'BRAND_ADMIN', 'PLATFORM_SUPER_ADMIN')")
    public ApiResponse<ReviewInvitationDto> getInvitationByVisit(@PathVariable UUID visitId) {
        return ApiResponse.ok(reviewInvitationService.getByVisitId(visitId));
    }

    @GetMapping("/guest-voice")
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'PLATFORM_SUPER_ADMIN')")
    public ApiResponse<GuestVoiceSummaryDto> guestVoice(
            @RequestParam(required = false) List<UUID> branchIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        UUID tenantId = SecurityUtils.requireTenantId();
        return ApiResponse.ok(guestVoiceAnalyticsService.summarize(tenantId, branchIds, from, to));
    }
}
