package com.salonplatform.sales.repository;

import com.salonplatform.sales.domain.entity.SalesLead;
import com.salonplatform.sales.domain.enums.LeadSource;
import com.salonplatform.sales.domain.enums.LeadStage;
import com.salonplatform.sales.domain.enums.LeadType;
import com.salonplatform.sales.dto.SalesLeadListFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SalesLeadSpecifications {

    private SalesLeadSpecifications() {}

    public static Specification<SalesLead> fromFilter(SalesLeadListFilter filter, UUID currentRepId, boolean isSalesExec) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (isSalesExec && Boolean.TRUE.equals(filter.getMineOnly())) {
                predicates.add(cb.equal(root.get("assignedRepId"), currentRepId));
            } else if (filter.getAssignedRepIds() != null && !filter.getAssignedRepIds().isEmpty()) {
                predicates.add(root.get("assignedRepId").in(filter.getAssignedRepIds()));
            } else if (filter.getAssignedRepId() != null) {
                predicates.add(cb.equal(root.get("assignedRepId"), filter.getAssignedRepId()));
            }

            if (filter.getStage() != null) {
                predicates.add(cb.equal(root.get("stage"), filter.getStage()));
            }
            if (filter.getLeadType() != null) {
                predicates.add(cb.equal(root.get("leadType"), filter.getLeadType()));
            }
            if (filter.getSource() != null) {
                predicates.add(cb.equal(root.get("source"), filter.getSource()));
            }
            if (filter.getLocalityId() != null) {
                if (filter.getLocalityName() != null && !filter.getLocalityName().isBlank()) {
                    String name = filter.getLocalityName().trim().toLowerCase();
                    predicates.add(cb.or(
                            cb.equal(root.get("localityId"), filter.getLocalityId()),
                            cb.equal(cb.lower(root.get("localityName")), name)
                    ));
                } else {
                    predicates.add(cb.equal(root.get("localityId"), filter.getLocalityId()));
                }
            }
            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String pattern = "%" + filter.getSearch().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("businessName")), pattern),
                        cb.like(cb.lower(root.get("contactName")), pattern),
                        cb.like(cb.lower(root.get("phone")), pattern),
                        cb.like(cb.lower(root.get("localityName")), pattern)
                ));
            }
            if (filter.getCreatedFrom() != null) {
                Instant from = filter.getCreatedFrom().atStartOfDay(ZoneId.systemDefault()).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (filter.getCreatedTo() != null) {
                Instant to = filter.getCreatedTo().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
