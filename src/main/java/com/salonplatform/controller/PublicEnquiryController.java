package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.enquiry.CreateEnquiryRequest;
import com.salonplatform.dto.enquiry.EnquiryResponse;
import com.salonplatform.service.EnquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/enquiries")
@RequiredArgsConstructor
public class PublicEnquiryController {

    private final EnquiryService enquiryService;

    @PostMapping
    public ApiResponse<EnquiryResponse> create(@Valid @RequestBody CreateEnquiryRequest request) {
        return ApiResponse.ok("Enquiry submitted", enquiryService.create(request));
    }
}
