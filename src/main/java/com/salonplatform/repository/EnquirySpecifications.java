package com.salonplatform.repository;

import com.salonplatform.domain.entity.MarketingEnquiry;
import com.salonplatform.dto.enquiry.EnquiryListFilter;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public final class EnquirySpecifications {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private EnquirySpecifications() {}

    public static Specification<MarketingEnquiry> fromFilter(UUID tenantId, EnquiryListFilter filter) {
        Specification<MarketingEnquiry> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);

        if (filter.getName() != null && !filter.getName().isBlank()) {
            String q = "%" + filter.getName().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), q));
        }
        if (filter.getSociety() != null && !filter.getSociety().isBlank()) {
            String q = "%" + filter.getSociety().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("society")), q));
        }
        if (filter.getEmail() != null && !filter.getEmail().isBlank()) {
            String q = "%" + filter.getEmail().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("email")), q));
        }
        if (filter.getMobile() != null && !filter.getMobile().isBlank()) {
            String q = "%" + filter.getMobile().trim() + "%";
            spec = spec.and((root, query, cb) -> cb.like(root.get("mobile"), q));
        }
        if (filter.getMessage() != null && !filter.getMessage().isBlank()) {
            String q = "%" + filter.getMessage().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("message")), q));
        }
        if (filter.getDateFrom() != null) {
            Instant start = filter.getDateFrom().atStartOfDay(ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (filter.getDateTo() != null) {
            Instant end = filter.getDateTo().plusDays(1).atStartOfDay(ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
        }

        return spec;
    }
}
