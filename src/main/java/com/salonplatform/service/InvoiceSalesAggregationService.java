package com.salonplatform.service;

import com.salonplatform.domain.entity.Booking;
import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.repository.BookingLineItemRepository;
import com.salonplatform.domain.repository.BookingRepository;
import com.salonplatform.dto.billing.BillLinePreview;
import com.salonplatform.dto.billing.BillPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates list vs final (post-discount) amounts from invoiced visits — single source for staff & service analytics.
 */
@Service
@RequiredArgsConstructor
public class InvoiceSalesAggregationService {

    private final BookingLineItemRepository lineItemRepository;
    private final BookingRepository bookingRepository;
    private final GstCalculationService gstCalculationService;
    private final PromoResolutionService promoResolutionService;

    public Map<UUID, StaffLineAggregate> aggregateByStaff(List<Invoice> invoices) {
        Map<UUID, BookingLineAggregate> byStaff = new HashMap<>();
        for (Invoice invoice : invoices) {
            List<BookingLineItem> lines = lineItemRepository.findByBookingId(invoice.getBookingId());
            if (lines.isEmpty()) {
                continue;
            }
            BillPreviewResponse bill = billPreviewForInvoice(invoice, lines);
            Map<UUID, BillLinePreview> previewByLineId = previewIndex(bill);
            for (BookingLineItem line : lines) {
                if (line.getStaffId() == null) {
                    continue;
                }
                int qty = line.getQuantity() != null ? line.getQuantity() : 1;
                BigDecimal listAmount = line.getUnitPrice().multiply(BigDecimal.valueOf(qty));
                BillLinePreview preview = previewByLineId.get(line.getId());
                BigDecimal finalAmount = preview != null && preview.getLineTotal() != null
                        ? preview.getLineTotal()
                        : listAmount;
                byStaff.compute(line.getStaffId(), (k, acc) -> {
                    BookingLineAggregate current = acc == null ? new BookingLineAggregate() : acc;
                    return current.add(listAmount, finalAmount, qty);
                });
            }
        }
        Map<UUID, StaffLineAggregate> result = new HashMap<>();
        byStaff.forEach((staffId, acc) -> result.put(staffId, acc.toStaffAggregate()));
        return result;
    }

    public Map<String, ServiceLineAggregate> aggregateByServiceName(List<Invoice> invoices) {
        Map<String, ServiceLineAggregate> byService = new HashMap<>();
        for (Invoice invoice : invoices) {
            List<BookingLineItem> lines = lineItemRepository.findByBookingId(invoice.getBookingId());
            if (lines.isEmpty()) {
                continue;
            }
            BillPreviewResponse bill = billPreviewForInvoice(invoice, lines);
            Map<UUID, BillLinePreview> previewByLineId = previewIndex(bill);
            for (BookingLineItem line : lines) {
                if (line.getServiceName() == null) {
                    continue;
                }
                int qty = line.getQuantity() != null ? line.getQuantity() : 1;
                BigDecimal listAmount = line.getUnitPrice().multiply(BigDecimal.valueOf(qty));
                BillLinePreview preview = previewByLineId.get(line.getId());
                BigDecimal finalAmount = preview != null && preview.getLineTotal() != null
                        ? preview.getLineTotal()
                        : listAmount;
                byService.compute(line.getServiceName(), (k, acc) -> {
                    ServiceLineAggregate current = acc == null ? new ServiceLineAggregate() : acc;
                    return current.add(listAmount, finalAmount, qty);
                });
            }
        }
        return byService;
    }

    private Map<UUID, BillLinePreview> previewIndex(BillPreviewResponse bill) {
        if (bill == null || bill.getLines() == null) {
            return Map.of();
        }
        return bill.getLines().stream()
                .filter(l -> l.getLineItemId() != null)
                .collect(Collectors.toMap(BillLinePreview::getLineItemId, l -> l, (a, b) -> a));
    }

    private BillPreviewResponse billPreviewForInvoice(Invoice invoice, List<BookingLineItem> lines) {
        Booking booking = bookingRepository.findById(invoice.getBookingId()).orElse(null);
        if (booking == null) {
            return null;
        }
        GstCalculationService.PromoContext promo = promoResolutionService.resolveForBooking(
                booking.getTenantId(),
                booking.getBranchId(),
                booking.getCustomerId(),
                booking.getCouponId(),
                booking.getOfferId(),
                booking.getPendingMembershipPlanId());
        return gstCalculationService.calculate(booking, lines, promo);
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
