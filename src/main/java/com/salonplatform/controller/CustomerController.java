package com.salonplatform.controller;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.customer.CreateCustomerRequest;
import com.salonplatform.dto.customer.CustomerListFilter;
import com.salonplatform.dto.customer.CustomerResponse;
import com.salonplatform.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @GetMapping
    public ApiResponse<PageResponse<CustomerResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String society,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Integer minVisitCount,
            @RequestParam(required = false) Integer maxVisitCount,
            @RequestParam(required = false) BigDecimal minLifetimeSpend,
            @RequestParam(required = false) BigDecimal maxLifetimeSpend,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lastVisitFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lastVisitTo,
            @RequestParam(required = false) Boolean whatsappOptInOnly,
            @RequestParam(required = false) Boolean smsOptInOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CustomerListFilter filter = CustomerListFilter.builder()
                .name(name)
                .society(society)
                .phone(phone)
                .minVisitCount(minVisitCount)
                .maxVisitCount(maxVisitCount)
                .minLifetimeSpend(minLifetimeSpend)
                .maxLifetimeSpend(maxLifetimeSpend)
                .lastVisitFrom(lastVisitFrom)
                .lastVisitTo(lastVisitTo)
                .whatsappOptInOnly(whatsappOptInOnly)
                .smsOptInOnly(smsOptInOnly)
                .page(page)
                .size(size)
                .build();
        return ApiResponse.ok(customerService.listPaged(filter));
    }
}
