package com.salonplatform.service;

import com.salonplatform.domain.entity.Booking;
import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.CustomerPackageSubscription;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.MembershipSubscription;
import com.salonplatform.domain.repository.BookingLineItemRepository;
import com.salonplatform.domain.repository.BookingRepository;
import com.salonplatform.domain.repository.CustomerPackageSubscriptionRepository;
import com.salonplatform.domain.repository.MembershipSubscriptionRepository;
import com.salonplatform.dto.billing.BillLinePreview;
import com.salonplatform.dto.billing.BillPreviewResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates list vs final (post-discount) amounts from invoiced visits — single source for staff & service analytics.
 */
@Service
@RequiredArgsConstructor
public class InvoiceSalesAggregationService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceSalesAggregationService.class);

    private final BookingLineItemRepository lineItemRepository;
    private final BookingRepository bookingRepository;
    private final GstCalculationService gstCalculationService;
    private final PromoResolutionService promoResolutionService;
    private final ServicePackageService servicePackageService;
    private final PackageStaffSaleImputationService packageStaffSaleImputationService;
    private final MembershipSubscriptionRepository membershipSubscriptionRepository;
    private final CustomerPackageSubscriptionRepository packageSubscriptionRepository;

    /**
     * Staff sales for a date range — booking line items (services, plus redemptions imputed by
     * {@link PackageStaffSaleImputationService}) PLUS membership and package sale amounts, credited
     * to whoever sold them. Membership and package purchases never create booking line items (a
     * membership sold standalone has no booking at all, and a package sold during checkout is only
     * recorded as a fee on the invoice), so without this second pass those sale amounts silently
     * vanish from every staff total.
     */
    public Map<UUID, StaffLineAggregate> aggregateByStaff(
            List<Invoice> invoices, UUID tenantId, Instant rangeStart, Instant rangeEnd, Set<UUID> branchFilter) {
        Map<UUID, List<BookingLineItem>> linesByBooking = fetchLinesByBooking(invoices);
        Map<UUID, Booking> bookingsById = fetchBookingsById(invoices);
        Map<UUID, BookingLineAggregate> byStaff = new HashMap<>();
        for (Invoice invoice : invoices) {
            List<BookingLineItem> lines = linesByBooking.getOrDefault(invoice.getBookingId(), List.of());
            if (lines.isEmpty()) {
                continue;
            }
            BillPreviewResponse bill = billPreviewForInvoice(invoice, bookingsById.get(invoice.getBookingId()), lines);
            Map<UUID, BillLinePreview> previewByLineId = previewIndex(bill);
            for (BookingLineItem line : lines) {
                if (line.getStaffId() == null) {
                    continue;
                }
                int qty = line.getQuantity() != null ? line.getQuantity() : 1;
                BillLinePreview preview = previewByLineId.get(line.getId());
                BigDecimal listAmount;
                BigDecimal finalAmount;
                PackageStaffSaleImputationService.ImputedLineAmounts imputed =
                        packageStaffSaleImputationService.imputeForStaffSale(line, preview);
                if (imputed != null) {
                    listAmount = imputed.listAmount();
                    finalAmount = imputed.finalAmount();
                } else {
                    listAmount = line.getUnitPrice().multiply(BigDecimal.valueOf(qty));
                    finalAmount = preview != null && preview.getLineTotal() != null
                            ? preview.getLineTotal()
                            : listAmount;
                }
                byStaff.compute(line.getStaffId(), (k, acc) -> {
                    BookingLineAggregate current = acc == null ? new BookingLineAggregate() : acc;
                    return current.add(listAmount, finalAmount, qty);
                });
            }
        }
        addPromoSales(byStaff, tenantId, rangeStart, rangeEnd, branchFilter);
        Map<UUID, StaffLineAggregate> result = new HashMap<>();
        byStaff.forEach((staffId, acc) -> result.put(staffId, acc.toStaffAggregate()));
        return result;
    }

    private void addPromoSales(
            Map<UUID, BookingLineAggregate> byStaff,
            UUID tenantId,
            Instant rangeStart,
            Instant rangeEnd,
            Set<UUID> branchFilter) {
        for (MembershipSubscription sub : membershipSubscriptionRepository
                .findByTenantIdAndSoldByStaffIdIsNotNull(tenantId)) {
            addPromoSale(byStaff, sub.getSoldByStaffId(), sub.getBranchId(), sub.getAmountPaid(),
                    sub.getCreatedAt(), rangeStart, rangeEnd, branchFilter);
        }
        for (CustomerPackageSubscription sub : packageSubscriptionRepository
                .findByTenantIdAndSoldByStaffIdIsNotNull(tenantId)) {
            addPromoSale(byStaff, sub.getSoldByStaffId(), sub.getBranchId(), sub.getAmountPaid(),
                    sub.getCreatedAt(), rangeStart, rangeEnd, branchFilter);
        }
    }

    private static void addPromoSale(
            Map<UUID, BookingLineAggregate> byStaff,
            UUID staffId,
            UUID branchId,
            BigDecimal amountPaid,
            Instant soldAt,
            Instant rangeStart,
            Instant rangeEnd,
            Set<UUID> branchFilter) {
        if (staffId == null) {
            return;
        }
        if (branchFilter != null && !branchFilter.contains(branchId)) {
            return;
        }
        if (rangeStart != null && (soldAt == null || soldAt.isBefore(rangeStart))) {
            return;
        }
        if (rangeEnd != null && (soldAt == null || !soldAt.isBefore(rangeEnd))) {
            return;
        }
        BigDecimal amount = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        byStaff.compute(staffId, (k, acc) -> {
            BookingLineAggregate current = acc == null ? new BookingLineAggregate() : acc;
            return current.add(amount, amount, 1);
        });
    }

    public Map<String, ServiceLineAggregate> aggregateByServiceName(List<Invoice> invoices) {
        Map<UUID, List<BookingLineItem>> linesByBooking = fetchLinesByBooking(invoices);
        Map<UUID, Booking> bookingsById = fetchBookingsById(invoices);
        Map<String, ServiceLineAggregate> byService = new HashMap<>();
        for (Invoice invoice : invoices) {
            List<BookingLineItem> lines = linesByBooking.getOrDefault(invoice.getBookingId(), List.of());
            if (lines.isEmpty()) {
                continue;
            }
            BillPreviewResponse bill = billPreviewForInvoice(invoice, bookingsById.get(invoice.getBookingId()), lines);
            Map<UUID, BillLinePreview> previewByLineId = previewIndex(bill);
            for (BookingLineItem line : lines) {
                if (line.getServiceName() == null) {
                    continue;
                }
                int qty = line.getQuantity() != null ? line.getQuantity() : 1;
                BillLinePreview preview = previewByLineId.get(line.getId());
                BigDecimal listAmount;
                BigDecimal finalAmount;
                PackageStaffSaleImputationService.ImputedLineAmounts imputed =
                        packageStaffSaleImputationService.imputeForStaffSale(line, preview);
                if (imputed != null) {
                    listAmount = imputed.listAmount();
                    finalAmount = imputed.finalAmount();
                } else {
                    listAmount = line.getUnitPrice().multiply(BigDecimal.valueOf(qty));
                    finalAmount = preview != null && preview.getLineTotal() != null
                            ? preview.getLineTotal()
                            : listAmount;
                }
                byService.compute(line.getServiceName(), (k, acc) -> {
                    ServiceLineAggregate current = acc == null ? new ServiceLineAggregate() : acc;
                    return current.add(listAmount, finalAmount, qty);
                });
            }
        }
        return byService;
    }

    /** One bulk query for every invoice's line items instead of a findByBookingId() call per invoice. */
    private Map<UUID, List<BookingLineItem>> fetchLinesByBooking(List<Invoice> invoices) {
        List<UUID> bookingIds = invoices.stream().map(Invoice::getBookingId).distinct().collect(Collectors.toList());
        return lineItemRepository.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.groupingBy(BookingLineItem::getBookingId));
    }

    /** One bulk query for every invoice's booking instead of a findById() call per invoice. */
    private Map<UUID, Booking> fetchBookingsById(List<Invoice> invoices) {
        List<UUID> bookingIds = invoices.stream().map(Invoice::getBookingId).distinct().collect(Collectors.toList());
        return bookingRepository.findAllById(bookingIds).stream()
                .collect(Collectors.toMap(Booking::getId, b -> b));
    }

    private Map<UUID, BillLinePreview> previewIndex(BillPreviewResponse bill) {
        if (bill == null || bill.getLines() == null) {
            return Map.of();
        }
        return bill.getLines().stream()
                .filter(l -> l.getLineItemId() != null)
                .collect(Collectors.toMap(BillLinePreview::getLineItemId, l -> l, (a, b) -> a));
    }

    /**
     * Rebuilds a per-line bill breakdown for an already-paid invoice, purely to attribute revenue
     * by staff/service. The coupon or offer applied here was valid when the sale was made — that
     * it may since have expired or been deactivated must not stop reporting on the past sale, so
     * promo resolution failures fall back to no-promo instead of throwing (mirrors
     * BookingService#billPreviewForList's handling of the same non-live-checkout case).
     */
    private BillPreviewResponse billPreviewForInvoice(Invoice invoice, Booking booking, List<BookingLineItem> lines) {
        if (booking == null) {
            return null;
        }
        GstCalculationService.PromoContext promo;
        try {
            promo = promoResolutionService.resolveForBooking(
                    booking.getTenantId(),
                    booking.getBranchId(),
                    booking.getCustomerId(),
                    booking.getCouponId(),
                    booking.getOfferId(),
                    booking.getPendingMembershipPlanId(),
                    booking.getPendingPackagePlanId());
        } catch (BadRequestException | ResourceNotFoundException ex) {
            log.warn("Skipping promo re-resolution for historical invoice {} (booking {}): {}",
                    invoice.getId(), booking.getId(), ex.getMessage());
            promo = GstCalculationService.PromoContext.empty();
        }
        BillPreviewResponse bill = gstCalculationService.calculate(booking, lines, promo);
        return servicePackageService.applyValueCreditToPreview(bill, lines);
    }

    public static final class StaffLineAggregate {
        private final BigDecimal listRevenue;
        private final BigDecimal finalRevenue;
        private final long serviceCount;

        StaffLineAggregate(BigDecimal listRevenue, BigDecimal finalRevenue, long serviceCount) {
            this.listRevenue = listRevenue;
            this.finalRevenue = finalRevenue;
            this.serviceCount = serviceCount;
        }

        public BigDecimal listRevenue() {
            return listRevenue;
        }

        public BigDecimal finalRevenue() {
            return finalRevenue;
        }

        public long serviceCount() {
            return serviceCount;
        }

        public BigDecimal avgFinalTicket() {
            if (serviceCount <= 0) {
                return BigDecimal.ZERO;
            }
            return finalRevenue.divide(BigDecimal.valueOf(serviceCount), 2, RoundingMode.HALF_UP);
        }
    }

    public static final class ServiceLineAggregate {
        private BigDecimal listRevenue = BigDecimal.ZERO;
        private BigDecimal finalRevenue = BigDecimal.ZERO;
        private long count;

        ServiceLineAggregate add(BigDecimal listAmount, BigDecimal finalAmount, int qty) {
            int q = Math.max(1, qty);
            listRevenue = listRevenue.add(listAmount);
            finalRevenue = finalRevenue.add(finalAmount);
            count += q;
            return this;
        }

        public BigDecimal listRevenue() {
            return listRevenue;
        }

        public BigDecimal finalRevenue() {
            return finalRevenue;
        }

        public long count() {
            return count;
        }
    }

    private static final class BookingLineAggregate {
        private BigDecimal listRevenue = BigDecimal.ZERO;
        private BigDecimal finalRevenue = BigDecimal.ZERO;
        private long serviceCount;

        BookingLineAggregate add(BigDecimal listAmount, BigDecimal finalAmount, int qty) {
            int q = Math.max(1, qty);
            listRevenue = listRevenue.add(listAmount);
            finalRevenue = finalRevenue.add(finalAmount);
            serviceCount += q;
            return this;
        }

        StaffLineAggregate toStaffAggregate() {
            return new StaffLineAggregate(listRevenue, finalRevenue, serviceCount);
        }
    }
}
