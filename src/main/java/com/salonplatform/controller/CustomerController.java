package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.customer.CreateCustomerRequest;
import com.salonplatform.dto.customer.CustomerResponse;
import com.salonplatform.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ApiResponse.ok(customerService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(customerService.getById(id));
    }

    @GetMapping("/phone/{phone}")
    public ApiResponse<CustomerResponse> byPhone(@PathVariable String phone) {
        return ApiResponse.ok(customerService.findByPhone(phone));
    }

    @GetMapping("/search")
    public ApiResponse<List<CustomerResponse>> search(@RequestParam String q) {
        return ApiResponse.ok(customerService.search(q));
    }
}
