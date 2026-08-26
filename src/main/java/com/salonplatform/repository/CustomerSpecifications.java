package com.salonplatform.repository;

import com.salonplatform.domain.entity.Customer;
import com.salonplatform.dto.customer.CustomerListFilter;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public final class CustomerSpecifications {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private CustomerSpecifications() {}

    public static Specification<Customer> fromFilter(UUID tenantId, CustomerListFilter filter) {
        Specification<Customer> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);

        if (filter.getNames() != null && !filter.getNames().isEmpty()) {
            List<String> names = filter.getNames().stream()
                    .filter(n -> n != null && !n.isBlank())
                    .map(n -> n.trim().toLowerCase())
                    .distinct()
                    .toList();
            if (!names.isEmpty()) {
                spec = spec.and((root, query, cb) -> {
                    Predicate[] ors = names.stream()
                            .map(n -> cb.equal(cb.lower(root.get("name")), n))
                            .toArray(Predicate[]::new);
                    return cb.or(ors);
                });
            }
        } else if (filter.getName() != null && !filter.getName().isBlank()) {
            String q = "%" + filter.getName().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), q));
        }
        if (filter.getSociety() != null && !filter.getSociety().isBlank()) {
            String q = "%" + filter.getSociety().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("society")), q));
        }
        if (filter.getPhones() != null && !filter.getPhones().isEmpty()) {
            List<String> phones = filter.getPhones().stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            if (!phones.isEmpty()) {
                spec = spec.and((root, query, cb) -> {
                    Predicate[] ors = phones.stream()
                            .map(p -> cb.equal(root.get("phone"), p))
                            .toArray(Predicate[]::new);
                    return cb.or(ors);
                });
            }
        } else if (filter.getPhone() != null && !filter.getPhone().isBlank()) {
            String q = "%" + filter.getPhone().trim() + "%";
            spec = spec.and((root, query, cb) -> cb.like(root.get("phone"), q));
        }
        if (filter.getVisitPassId() != null && !filter.getVisitPassId().isBlank()) {
            String q = "%" + filter.getVisitPassId().trim().toUpperCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.upper(root.get("visitPassId")), q));
        }
        if (filter.getMinVisitCount() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("visitCount"), filter.getMinVisitCount()));
        }
        if (filter.getMaxVisitCount() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("visitCount"), filter.getMaxVisitCount()));
        }
        if (filter.getMinLifetimeSpend() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("lifetimeSpend"), filter.getMinLifetimeSpend()));
        }
        if (filter.getMaxLifetimeSpend() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("lifetimeSpend"), filter.getMaxLifetimeSpend()));
        }
        if (filter.getLastVisitFrom() != null) {
            Instant start = filter.getLastVisitFrom().atStartOfDay(ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("lastVisitAt"), start));
        }
        if (filter.getLastVisitTo() != null) {
            Instant end = filter.getLastVisitTo().plusDays(1).atStartOfDay(ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("lastVisitAt"), end));
        }
        if (Boolean.TRUE.equals(filter.getWhatsappOptInOnly())) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("whatsappOptIn")));
        }
        if (Boolean.TRUE.equals(filter.getSmsOptInOnly())) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("smsOptIn")));
        }

        return spec;
    }

    public static Specification<Customer> fromCampaignFilters(
            UUID tenantId,
            List<String> names,
            List<String> phones,
            String society,
            Integer minVisitCount,
            Integer maxVisitCount,
            BigDecimal minLifetimeSpend,
            BigDecimal maxLifetimeSpend,
            java.time.LocalDate lastVisitFrom,
            java.time.LocalDate lastVisitTo,
            Boolean whatsappOptInOnly,
            Boolean smsOptInOnly) {
        return fromFilter(tenantId, CustomerListFilter.builder()
                .names(names)
                .phones(phones)
                .society(society)
                .minVisitCount(minVisitCount)
                .maxVisitCount(maxVisitCount)
                .minLifetimeSpend(minLifetimeSpend)
                .maxLifetimeSpend(maxLifetimeSpend)
                .lastVisitFrom(lastVisitFrom)
                .lastVisitTo(lastVisitTo)
                .whatsappOptInOnly(whatsappOptInOnly)
                .smsOptInOnly(smsOptInOnly)
                .build());
    }
}
