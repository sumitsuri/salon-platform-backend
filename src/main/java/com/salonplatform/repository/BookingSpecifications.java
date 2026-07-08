package com.salonplatform.repository;

import com.salonplatform.domain.entity.Booking;
import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.dto.booking.BookingListFilter;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public final class BookingSpecifications {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private BookingSpecifications() {}

    public static Specification<Booking> fromFilter(UUID tenantId, BookingListFilter filter) {
        Specification<Booking> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);

        if (filter.getBranchId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("branchId"), filter.getBranchId()));
        }
        if (filter.getStatus() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.getStatus()));
        }
        if (filter.getCustomer() != null && !filter.getCustomer().isBlank()) {
            String q = "%" + filter.getCustomer().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                var sq = query.subquery(Long.class);
                var customer = sq.from(Customer.class);
                sq.select(cb.literal(1L));
                sq.where(cb.and(
                        cb.equal(customer.get("id"), root.get("customerId")),
                        cb.or(
                                cb.like(cb.lower(customer.get("name")), q),
                                cb.like(customer.get("phone"), "%" + filter.getCustomer().trim() + "%")
                        )
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
        if (filter.getDateFrom() != null) {
            Instant start = filter.getDateFrom().atStartOfDay(ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (filter.getDateTo() != null) {
            Instant end = filter.getDateTo().plusDays(1).atStartOfDay(ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
        }
        if (filter.getMinAmount() != null) {
            spec = spec.and(amountAtLeast(filter.getMinAmount()));
        }
        if (filter.getMaxAmount() != null) {
            spec = spec.and(amountAtMost(filter.getMaxAmount()));
        }
        if (filter.getService() != null && !filter.getService().isBlank()) {
            String q = "%" + filter.getService().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                var sq = query.subquery(Long.class);
                var line = sq.from(BookingLineItem.class);
                sq.select(cb.literal(1L));
                sq.where(cb.and(
                        cb.equal(line.get("bookingId"), root.get("id")),
                        cb.like(cb.lower(line.get("serviceName")), q)
                ));
                return cb.exists(sq);
            });
        }
        if (filter.getStylist() != null && !filter.getStylist().isBlank()) {
            String q = "%" + filter.getStylist().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                var sq = query.subquery(Long.class);
                var line = sq.from(BookingLineItem.class);
                var staff = sq.from(Staff.class);
                sq.select(cb.literal(1L));
                sq.where(cb.and(
                        cb.equal(line.get("bookingId"), root.get("id")),
                        cb.equal(line.get("staffId"), staff.get("id")),
                        cb.like(cb.lower(staff.get("name")), q)
                ));
                return cb.exists(sq);
            });
        }

        return spec;
    }

    private static Specification<Booking> amountAtLeast(java.math.BigDecimal min) {
        return (root, query, cb) -> {
            var sq = query.subquery(Long.class);
            var invoice = sq.from(Invoice.class);
            sq.select(cb.literal(1L));
            sq.where(cb.and(
                    cb.equal(invoice.get("bookingId"), root.get("id")),
                    cb.greaterThanOrEqualTo(invoice.get("grandTotal"), min)
            ));
            return cb.exists(sq);
        };
    }

    private static Specification<Booking> amountAtMost(java.math.BigDecimal max) {
        return (root, query, cb) -> {
            var sq = query.subquery(Long.class);
            var invoice = sq.from(Invoice.class);
            sq.select(cb.literal(1L));
            sq.where(cb.and(
                    cb.equal(invoice.get("bookingId"), root.get("id")),
                    cb.lessThanOrEqualTo(invoice.get("grandTotal"), max)
            ));
            return cb.exists(sq);
        };
    }
}
