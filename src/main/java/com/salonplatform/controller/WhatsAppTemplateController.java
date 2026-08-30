package com.salonplatform.controller;

import com.salonplatform.domain.enums.WhatsAppTemplateCategory;
import com.salonplatform.domain.enums.WhatsAppTemplateCode;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.whatsapp.*;
import com.salonplatform.service.WhatsAppTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/whatsapp-templates")
@RequiredArgsConstructor
public class WhatsAppTemplateController {

    private final WhatsAppTemplateService whatsAppTemplateService;

    @GetMapping
    public ApiResponse<List<WhatsAppTemplateResponse>> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) WhatsAppTemplateCategory category,
            @RequestParam(required = false) String search) {
        return ApiResponse.ok(whatsAppTemplateService.list(branchId, category, search));
    }

    @PatchMapping("/{code}")
    public ApiResponse<WhatsAppTemplateResponse> update(
            @PathVariable WhatsAppTemplateCode code,
            @RequestBody UpdateWhatsAppTemplateSettingRequest request) {
        return ApiResponse.ok(whatsAppTemplateService.updateSetting(code, request));
    }

    @PostMapping("/{code}/preview")
    public ApiResponse<WhatsAppTemplatePreviewResponse> preview(
            @PathVariable WhatsAppTemplateCode code,
            @RequestBody(required = false) WhatsAppTemplatePreviewRequest request) {
        return ApiResponse.ok(whatsAppTemplateService.preview(code, request));
    }
}
