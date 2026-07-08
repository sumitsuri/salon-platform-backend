package com.salonplatform.repository;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.LeaveRecord;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.dto.leave.LeaveListFilter;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class LeaveSpecifications {

    private LeaveSpecifications() {}

    public static Specification<LeaveRecord> fromFilter(UUID tenantId, LeaveListFilter filter) {
        Specification<LeaveRecord> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);

        if (filter.getBranchId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("branchId"), filter.getBranchId()));
        }
        if (filter.getStatus() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.getStatus()));
        }
        if (filter.getDateFrom() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("endDate"), filter.getDateFrom()));
        }
        if (filter.getDateTo() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("startDate"), filter.getDateTo()));
        }
        if (filter.getStaff() != null && !filter.getStaff().isBlank()) {
            String q = "%" + filter.getStaff().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                var sq = query.subquery(Long.class);
                var staff = sq.from(Staff.class);
                sq.select(cb.literal(1L));
                sq.where(cb.and(
                        cb.equal(staff.get("id"), root.get("staffId")),
                        cb.like(cb.lower(staff.get("name")), q)
                ));
                return cb.exists(sq);
            });
        }
        if (filter.getBranch() != null && !filter.getBranch().isBlank()) {
            String q = "%" + filter.getBranch().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                var sq = query.subquery(Long.class);
                var branch = sq.from(Branch.class);
                sq.select(cb.literal(1L));
                sq.where(cb.and(
                        cb.equal(branch.get("id"), root.get("branchId")),
                        cb.like(cb.lower(branch.get("name")), q)
                ));
                return cb.exists(sq);
            });
        }

        return spec;
    }
}
