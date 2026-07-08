package com.salonplatform.repository;

import com.salonplatform.domain.entity.AttendanceRecord;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.dto.attendance.AttendanceListFilter;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class AttendanceSpecifications {

    private AttendanceSpecifications() {}

    public static Specification<AttendanceRecord> fromFilter(UUID tenantId, AttendanceListFilter filter) {
        Specification<AttendanceRecord> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);

        if (filter.getBranchId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("branchId"), filter.getBranchId()));
        }
        if (filter.getDateFrom() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("workDate"), filter.getDateFrom()));
        }
        if (filter.getDateTo() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("workDate"), filter.getDateTo()));
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
        if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
            String status = filter.getStatus().trim().toUpperCase();
            spec = spec.and(switch (status) {
                case "COMPLETED" -> (root, query, cb) -> cb.and(
                        cb.isNotNull(root.get("entryTime")),
                        cb.isNotNull(root.get("exitTime")));
                case "PRESENT" -> (root, query, cb) -> cb.and(
                        cb.isNotNull(root.get("entryTime")),
                        cb.isNull(root.get("exitTime")));
                case "ABSENT" -> (root, query, cb) -> cb.isNull(root.get("entryTime"));
                default -> (root, query, cb) -> cb.conjunction();
            });
        }

        return spec;
    }
}
