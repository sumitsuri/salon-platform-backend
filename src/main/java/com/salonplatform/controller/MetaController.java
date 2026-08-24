package com.salonplatform.controller;

import com.salonplatform.config.LocaleProperties;
import com.salonplatform.config.Msg91Properties;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.meta.LocaleInfoResponse;
import com.salonplatform.dto.meta.MessagingConfigResponse;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
public class MetaController {

    private final LocaleProperties localeProperties;
    private final Msg91Properties msg91Properties;

    @Value("${app.api-public-url:http://localhost:8080}")
    private String apiPublicUrl;

    @GetMapping("/locales")
    public ApiResponse<List<LocaleInfoResponse>> locales() {
        List<LocaleInfoResponse> list = localeProperties.enabledSorted().stream()
                .map(e -> LocaleInfoResponse.builder()
                        .code(e.code())
                        .label(e.label())
                        .nativeLabel(e.nativeLabel())
                        .stateCode(e.stateCode())
                        .stateName(e.stateName())
                        .stateNameNative(e.stateNameNative())
                        .regionGroup(e.regionGroup())
                        .sortOrder(e.sortOrder())
                        .build())
                .toList();
        return ApiResponse.ok(list);
    }

    @GetMapping("/messaging")
    public ApiResponse<MessagingConfigResponse> messaging() {
        SecurityUtils.assertBrandAdminOrAbove();
        return ApiResponse.ok(MessagingConfigResponse.builder()
                .msg91Enabled(msg91Properties.isEnabled())
                .whatsappNumber(msg91Properties.getWhatsappIntegratedNumber())
                .billReceiptTemplate(msg91Properties.getBillReceiptTemplate())
                .promoTemplate(msg91Properties.getPromoTemplate())
                .appointmentConfirmedTemplate(msg91Properties.getAppointmentConfirmedTemplate())
                .apiPublicUrl(apiPublicUrl)
                .billReceiptPilotEnabled(msg91Properties.isBillReceiptPilotEnabled())
                .billReceiptPilotTenantSlug(msg91Properties.getBillReceiptPilotTenantSlug())
                .billReceiptPilotBranchCode(msg91Properties.getBillReceiptPilotBranchCode())
                .build());
    }
}
