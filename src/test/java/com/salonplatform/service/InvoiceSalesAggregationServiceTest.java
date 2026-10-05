package com.salonplatform.service;

import com.salonplatform.domain.entity.Booking;
import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.repository.BookingLineItemRepository;
import com.salonplatform.domain.repository.BookingRepository;
import com.salonplatform.dto.billing.BillLinePreview;
import com.salonplatform.dto.billing.BillPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceSalesAggregationServiceTest {

    @Mock BookingLineItemRepository lineItemRepository;
    @Mock BookingRepository bookingRepository;
    @Mock GstCalculationService gstCalculationService;
    @Mock PromoResolutionService promoResolutionService;
    @Mock ServicePackageService servicePackageService;
    @Mock PackageStaffSaleImputationService imputationService;

    private InvoiceSalesAggregationService service;
    private final UUID bookingId = UUID.randomUUID();
    private final UUID riya = UUID.randomUUID();
    private final UUID arjun = UUID.randomUUID();
    private BookingLineItem spa;
    private BookingLineItem cut;

    @BeforeEach
    void setUp() {
        service = new InvoiceSalesAggregationService(lineItemRepository, bookingRepository, gstCalculationService,
                promoResolutionService, servicePackageService, imputationService);
        spa = line("Hair Spa", riya, "1500");
        cut = line("Haircut", arjun, "500");
        when(lineItemRepository.findByBookingIdIn(anyList())).thenReturn(List.of(spa, cut));
        Booking booking = Booking.builder().id(bookingId).tenantId(UUID.randomUUID()).branchId(UUID.randomUUID()).build();
        when(bookingRepository.findAllById(anyList())).thenReturn(List.of(booking));
        when(promoResolutionService.resolveForBooking(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(GstCalculationService.PromoContext.empty());
        when(servicePackageService.applyValueCreditToPreview(any(), anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    private BookingLineItem line(String name, UUID staff, String price) {
        return BookingLineItem.builder().id(UUID.randomUUID()).bookingId(bookingId).staffId(staff)
                .serviceName(name).unitPrice(new BigDecimal(price)).quantity(1).build();
    }

    private void billWithManualDiscount(String manual) {
        BillPreviewResponse bill = BillPreviewResponse.builder()
                .lines(List.of(
                        BillLinePreview.builder().lineItemId(spa.getId()).lineTotal(new BigDecimal("1500")).build(),
                        BillLinePreview.builder().lineItemId(cut.getId()).lineTotal(new BigDecimal("500")).build()))
                .manualDiscountAmount(new BigDecimal(manual))
                .build();
        when(gstCalculationService.calculate(any(), anyList(), any())).thenReturn(bill);
    }

    private List<Invoice> invoices() {
        return List.of(Invoice.builder().id(UUID.randomUUID()).bookingId(bookingId).build());
    }

    @Test
    void billLevelDiscountIsSpreadAcrossStaffFinalSales() {
        billWithManualDiscount("200"); // ₹2,000 bill, ₹200 manager discount → guest paid ₹1,800

        Map<UUID, InvoiceSalesAggregationService.StaffLineAggregate> byStaff = service.aggregateByStaff(invoices());

        assertEquals(new BigDecimal("1500"), byStaff.get(riya).listRevenue());
        assertEquals(new BigDecimal("1350.00"), byStaff.get(riya).finalRevenue());
        assertEquals(new BigDecimal("450.00"), byStaff.get(arjun).finalRevenue());
    }

    @Test
    void billLevelDiscountIsSpreadAcrossServiceFinalSales() {
        billWithManualDiscount("200");

        var byService = service.aggregateByServiceName(invoices());

        assertEquals(new BigDecimal("1350.00"), byService.get("Hair Spa").finalRevenue());
        assertEquals(new BigDecimal("450.00"), byService.get("Haircut").finalRevenue());
    }

    @Test
    void billsWithoutManualDiscountAreUnchanged() {
        billWithManualDiscount("0");

        var byStaff = service.aggregateByStaff(invoices());

        assertEquals(0, new BigDecimal("1500").compareTo(byStaff.get(riya).finalRevenue()));
        assertEquals(0, new BigDecimal("500").compareTo(byStaff.get(arjun).finalRevenue()));
    }
}
