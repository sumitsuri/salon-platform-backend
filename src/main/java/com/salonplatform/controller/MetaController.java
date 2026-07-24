package com.salonplatform.controller;

import com.salonplatform.config.LocaleProperties;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.meta.LocaleInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
public class MetaController {

    private final LocaleProperties localeProperties;

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
}
