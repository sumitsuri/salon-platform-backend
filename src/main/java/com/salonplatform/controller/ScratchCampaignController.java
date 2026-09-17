package com.salonplatform.controller;

import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.scratch.*;
import com.salonplatform.service.ScratchFootfallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scratch-campaigns")
@RequiredArgsConstructor
public class ScratchCampaignController {

    private final ScratchFootfallService scratchFootfallService;

    @GetMapping
    public ApiResponse<List<ScratchCampaignResponse>> list() {
        return ApiResponse.ok(scratchFootfallService.listCampaigns());
    }

    @PostMapping
    public ApiResponse<ScratchCampaignResponse> create(@Valid @RequestBody CreateScratchCampaignRequest request) {
        return ApiResponse.ok(scratchFootfallService.createCampaign(request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ScratchCampaignResponse> status(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(scratchFootfallService.updateCampaignStatus(id, PromoStatus.valueOf(body.get("status"))));
    }

    @GetMapping("/active")
    public ApiResponse<List<ScratchCampaignResponse>> activeForBranch(@RequestParam UUID branchId) {
        return ApiResponse.ok(scratchFootfallService.listActiveCampaignsForBranch(branchId));
    }
}
