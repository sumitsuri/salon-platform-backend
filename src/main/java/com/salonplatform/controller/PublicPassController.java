package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.customer.CustomerRegistrationCardResponse;
import com.salonplatform.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/pass")
@RequiredArgsConstructor
public class PublicPassController {

    private final CustomerService customerService;

    @GetMapping("/{token}")
    public ApiResponse<CustomerRegistrationCardResponse> getPass(@PathVariable String token) {
        return ApiResponse.ok(customerService.getRegistrationCardByPublicToken(token));
    }
}
