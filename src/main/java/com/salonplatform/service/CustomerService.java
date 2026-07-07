package com.salonplatform.service;

import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.repository.CustomerRepository;
import com.salonplatform.dto.customer.CreateCustomerRequest;
import com.salonplatform.dto.customer.CustomerResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        UUID tenantId = SecurityUtils.requireTenantId();
        customerRepository.findByTenantIdAndPhone(tenantId, request.getPhone()).ifPresent(c -> {
            throw new BadRequestException("Customer with this phone already exists");
        });
        Customer customer = customerRepository.save(Customer.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .phone(request.getPhone())
                .society(request.getSociety())
                .flatUnit(request.getFlatUnit())
                .notes(request.getNotes())
                .visitCount(0)
                .lifetimeSpend(BigDecimal.ZERO)
                .build());
        return toResponse(customer);
    }

    public CustomerResponse getById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return toResponse(customer);
    }

    public CustomerResponse findByPhone(String phone) {
        UUID tenantId = SecurityUtils.requireTenantId();
        Customer customer = customerRepository.findByTenantIdAndPhone(tenantId, phone)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return toResponse(customer);
    }

    public List<CustomerResponse> search(String query) {
        UUID tenantId = SecurityUtils.requireTenantId();
        return customerRepository.search(tenantId, query).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private CustomerResponse toResponse(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .phone(c.getPhone())
                .society(c.getSociety())
                .flatUnit(c.getFlatUnit())
                .notes(c.getNotes())
                .visitCount(c.getVisitCount())
                .lifetimeSpend(c.getLifetimeSpend())
                .lastVisitAt(c.getLastVisitAt())
                .build();
    }
}
