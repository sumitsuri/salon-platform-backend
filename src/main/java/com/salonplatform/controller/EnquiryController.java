package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.enquiry.EnquiryListFilter;
import com.salonplatform.dto.enquiry.EnquiryResponse;
import com.salonplatform.service.EnquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/enquiries")
@RequiredArgsConstructor
public class EnquiryController {

    private final EnquiryService enquiryService;

    @GetMapping
    public ApiResponse<PageResponse<EnquiryResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String society,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        EnquiryListFilter filter = EnquiryListFilter.builder()
                .name(name)
                .society(society)
                .email(email)
                .mobile(mobile)
                .message(message)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .page(page)
                .size(size)
                .build();
        return ApiResponse.ok(enquiryService.listPaged(filter));
    }
}
