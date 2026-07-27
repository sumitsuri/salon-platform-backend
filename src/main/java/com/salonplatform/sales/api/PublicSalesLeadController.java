package com.salonplatform.sales.api;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.sales.application.PublicSalesLeadService;
import com.salonplatform.sales.dto.CreatePublicSalesLeadRequest;
import com.salonplatform.sales.dto.SalesLeadResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/sales-leads")
@RequiredArgsConstructor
public class PublicSalesLeadController {

    private final PublicSalesLeadService publicSalesLeadService;

    @PostMapping
    public ApiResponse<SalesLeadResponse> create(@Valid @RequestBody CreatePublicSalesLeadRequest request) {
        return ApiResponse.ok("Lead submitted", publicSalesLeadService.createFromMarketing(request));
    }
}
