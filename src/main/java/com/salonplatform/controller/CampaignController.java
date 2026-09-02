package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.campaign.CampaignDeliveryResponse;
import com.salonplatform.dto.campaign.CampaignListFilter;
import com.salonplatform.dto.campaign.CampaignPreviewResponse;
import com.salonplatform.dto.campaign.CampaignResponse;
import com.salonplatform.dto.campaign.CreateCampaignRequest;
import com.salonplatform.domain.enums.CampaignStatus;
import com.salonplatform.domain.enums.MessageChannel;
import com.salonplatform.dto.campaign.CampaignTemplateLibraryResponse;
import com.salonplatform.dto.campaign.CampaignTemplateResponse;
import com.salonplatform.service.CampaignService;
import com.salonplatform.service.CampaignTemplateService;
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
    private final CampaignTemplateService campaignTemplateService;

    @GetMapping("/templates")
    public ApiResponse<CampaignTemplateLibraryResponse> templates() {
        return ApiResponse.ok(campaignTemplateService.listLibrary());
    }

    @GetMapping("/templates/{templateId}")
    public ApiResponse<CampaignTemplateResponse> template(@PathVariable String templateId) {
        return ApiResponse.ok(campaignTemplateService.getTemplate(templateId));
    }

    @GetMapping
    public ApiResponse<List<CampaignResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) MessageChannel channel,
            @RequestParam(required = false) CampaignStatus status) {
        CampaignListFilter filter = CampaignListFilter.builder()
                .name(name)
                .channel(channel)
                .status(status)
                .build();
        return ApiResponse.ok(campaignService.list(filter));
    }

    @GetMapping("/{id}/preview")
    public ApiResponse<CampaignPreviewResponse> previewCampaign(@PathVariable UUID id) {
        return ApiResponse.ok(campaignService.previewCampaign(id));
    }

    @GetMapping("/{id}/runs")
    public ApiResponse<List<com.salonplatform.dto.campaign.CampaignRunResponse>> runs(@PathVariable UUID id) {
        return ApiResponse.ok(campaignService.listRuns(id));
    }

    @GetMapping("/{id}/runs/{runId}/deliveries")
    public ApiResponse<List<CampaignDeliveryResponse>> runDeliveries(
            @PathVariable UUID id,
            @PathVariable UUID runId) {
        return ApiResponse.ok(campaignService.listRunDeliveries(id, runId));
    }

    @GetMapping("/{id}/deliveries")
    public ApiResponse<List<CampaignDeliveryResponse>> deliveries(@PathVariable UUID id) {
        return ApiResponse.ok(campaignService.listDeliveries(id));
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

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        campaignService.delete(id);
        return ApiResponse.ok(null);
    }
}
