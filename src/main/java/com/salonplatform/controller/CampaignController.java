package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.campaign.CampaignPreviewResponse;
import com.salonplatform.dto.campaign.CampaignResponse;
import com.salonplatform.dto.campaign.CreateCampaignRequest;
import com.salonplatform.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    public ApiResponse<List<CampaignResponse>> list() {
        return ApiResponse.ok(campaignService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<CampaignResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(campaignService.get(id));
    }

    @PostMapping("/preview")
    public ApiResponse<CampaignPreviewResponse> preview(@Valid @RequestBody CreateCampaignRequest request) {
        return ApiResponse.ok(campaignService.preview(request));
    }

    @PostMapping
    public ApiResponse<CampaignResponse> create(@Valid @RequestBody CreateCampaignRequest request) {
        return ApiResponse.ok(campaignService.create(request));
    }

    @PostMapping("/{id}/send")
    public ApiResponse<CampaignResponse> send(@PathVariable UUID id) {
        return ApiResponse.ok(campaignService.send(id));
    }
}
