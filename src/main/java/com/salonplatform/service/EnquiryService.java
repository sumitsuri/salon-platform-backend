package com.salonplatform.service;

import com.salonplatform.domain.entity.MarketingEnquiry;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.repository.MarketingEnquiryRepository;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.enquiry.CreateEnquiryRequest;
import com.salonplatform.dto.enquiry.EnquiryListFilter;
import com.salonplatform.dto.enquiry.EnquiryResponse;
import com.salonplatform.repository.EnquirySpecifications;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnquiryService {

    private static final String DEFAULT_TENANT_SLUG = "demo-brand";

    private final MarketingEnquiryRepository enquiryRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public EnquiryResponse create(CreateEnquiryRequest request) {
        String slug = request.getTenantSlug() != null && !request.getTenantSlug().isBlank()
                ? request.getTenantSlug().trim()
                : DEFAULT_TENANT_SLUG;

        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown brand"));

        MarketingEnquiry enquiry = MarketingEnquiry.builder()
                .tenantId(tenant.getId())
                .name(request.getName().trim())
                .society(request.getSociety() != null ? request.getSociety().trim() : null)
                .email(request.getEmail().trim().toLowerCase())
                .mobile(request.getMobile().trim())
                .message(request.getMessage().trim())
                .build();

        MarketingEnquiry saved = enquiryRepository.saveAndFlush(enquiry);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<EnquiryResponse> listPaged(EnquiryListFilter filter) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();

        int size = PageUtils.normalizeSize(filter.getSize());
        int page = PageUtils.normalizePage(filter.getPage());

        Specification<MarketingEnquiry> spec = EnquirySpecifications.fromFilter(tenantId, filter);
        Page<MarketingEnquiry> result = enquiryRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return PageResponse.<EnquiryResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    private EnquiryResponse toResponse(MarketingEnquiry enquiry) {
        return EnquiryResponse.builder()
                .id(enquiry.getId())
                .name(enquiry.getName())
                .society(enquiry.getSociety())
                .email(enquiry.getEmail())
                .mobile(enquiry.getMobile())
                .message(enquiry.getMessage())
                .createdAt(enquiry.getCreatedAt())
                .build();
    }
}
