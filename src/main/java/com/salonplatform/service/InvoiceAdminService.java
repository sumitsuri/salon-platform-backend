package com.salonplatform.service;

import com.salonplatform.domain.entity.Booking;
import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.Payment;
import com.salonplatform.domain.entity.PaymentSplit;
import com.salonplatform.domain.enums.BookingStatus;
import com.salonplatform.domain.enums.ScratchCardStatus;
import com.salonplatform.domain.repository.BookingLineItemRepository;
import com.salonplatform.domain.repository.BookingRepository;
import com.salonplatform.domain.repository.CustomerRepository;
import com.salonplatform.domain.repository.InvoiceRepository;
import com.salonplatform.domain.repository.PaymentRepository;
import com.salonplatform.domain.repository.PaymentSplitRepository;
import com.salonplatform.domain.repository.ScratchCardRepository;
import com.salonplatform.dto.billing.BillPreviewResponse;
import com.salonplatform.dto.invoice.AdminUpdateBillRequest;
import com.salonplatform.dto.invoice.InvoiceDetailResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.util.InvoiceBillUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceAdminService {

    private final InvoiceRepository invoiceRepository;
    private final BookingRepository bookingRepository;
    private final BookingLineItemRepository lineItemRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final GstCalculationService gstCalculationService;
    private final ServicePackageService servicePackageService;
    private final PromoResolutionService promoResolutionService;
    private final InvoicePdfService invoicePdfService;
    private final AuditService auditService;
    private final ScratchCardRepository scratchCardRepository;
    private final BookingService bookingService;

    @Transactional
    public InvoiceDetailResponse updateBill(UUID bookingId, AdminUpdateBillRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BadRequestException("error.invoice.onlyCompleted");
        }
        Invoice invoice = invoiceRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        List<BookingLineItem> existingLines = lineItemRepository.findByBookingId(bookingId);
        BillPreviewResponse priorBill = billPreviewFor(booking, existingLines);

        servicePackageService.reverseRedemptionsAfterPayment(booking, existingLines, priorBill);

        if (request.getLines() != null) {
            bookingService.adminReplaceLinesForInvoiceEdit(bookingId, request.getLines());
        }

        List<BookingLineItem> newLines = lineItemRepository.findByBookingId(bookingId);
        if (newLines.isEmpty()
                && feeAmount(invoice.getMembershipFeeAmount()).compareTo(BigDecimal.ZERO) <= 0
                && feeAmount(invoice.getPackageFeeAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("error.booking.servicesRequired");
        }

        syncInvoiceFromBooking(booking, invoice, newLines);

        List<BookingLineItem> linesAfter = lineItemRepository.findByBookingId(bookingId);
        BillPreviewResponse newBill = billPreviewFor(booking, linesAfter);
        servicePackageService.applyRedemptionsAfterPayment(booking, linesAfter, newBill);

        auditService.log(
                "ADMIN_UPDATE_BILL",
                "Invoice",
                invoice.getId(),
                request.getReason() != null ? request.getReason() : "Bill updated by admin");

        try {
            invoicePdfService.persistPdf(invoice);
        } catch (Exception ignored) {
            // PDF refresh is best-effort; download regenerates on demand.
        }

        return toDetail(invoiceRepository.findById(invoice.getId()).orElseThrow());
    }

    @Transactional
    public InvoiceDetailResponse recalculateInvoice(UUID invoiceId) {
        SecurityUtils.assertBrandAdminOrAbove();
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        SecurityUtils.assertBranchAccess(invoice.getBranchId());
        Booking booking = bookingRepository.findById(invoice.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BadRequestException("error.invoice.onlyCompleted");
        }

        List<BookingLineItem> lines = lineItemRepository.findByBookingId(booking.getId());
        BillPreviewResponse priorBill = billPreviewFor(booking, lines);
        servicePackageService.reverseRedemptionsAfterPayment(booking, lines, priorBill);

        syncInvoiceFromBooking(booking, invoice, lines);

        BillPreviewResponse newBill = billPreviewFor(booking, lines);
        servicePackageService.applyRedemptionsAfterPayment(booking, lines, newBill);

        auditService.log("ADMIN_RECALC_BILL", "Invoice", invoice.getId(), null);

        try {
            invoicePdfService.persistPdf(invoice);
        } catch (Exception ignored) {
        }

        return toDetail(invoiceRepository.findById(invoice.getId()).orElseThrow());
    }

    @Transactional
    public void voidInvoice(UUID invoiceId, String reason) {
        SecurityUtils.assertBrandAdminOrAbove();
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        SecurityUtils.assertBranchAccess(invoice.getBranchId());

        Booking booking = bookingRepository.findById(invoice.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BadRequestException("error.invoice.onlyCompleted");
        }

        assertVoidAllowed(booking, invoice);

        List<BookingLineItem> lines = lineItemRepository.findByBookingId(booking.getId());
        BillPreviewResponse bill = billPreviewFor(booking, lines);
        servicePackageService.reverseRedemptionsAfterPayment(booking, lines, bill);
        servicePackageService.voidPackagePurchasesForInvoice(invoice.getId());

        BigDecimal grandTotal = invoice.getGrandTotal() != null ? invoice.getGrandTotal() : BigDecimal.ZERO;

        List<Payment> payments = paymentRepository.findByBookingId(booking.getId());
        for (Payment payment : payments) {
            List<PaymentSplit> splits = paymentSplitRepository.findByPaymentId(payment.getId());
            paymentSplitRepository.deleteAll(splits);
            paymentRepository.delete(payment);
        }

        invoiceRepository.delete(invoice);

        Customer customer = customerRepository.findById(booking.getCustomerId()).orElseThrow();
        if (customer.getVisitCount() != null && customer.getVisitCount() > 0) {
            customer.setVisitCount(customer.getVisitCount() - 1);
        }
        BigDecimal spend = customer.getLifetimeSpend() != null ? customer.getLifetimeSpend() : BigDecimal.ZERO;
        customer.setLifetimeSpend(spend.subtract(grandTotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        customerRepository.save(customer);

        promoResolutionService.decrementRedemptions(booking.getCouponId(), booking.getOfferId());

        booking.setStatus(BookingStatus.READY_FOR_BILLING);
        booking.setCompletedAt(null);
        booking.setActualDurationMinutes(null);
        bookingRepository.save(booking);

        auditService.log(
                "ADMIN_VOID_BILL",
                "Booking",
                booking.getId(),
                reason != null && !reason.isBlank() ? reason : "Invoice voided by admin");
    }

    private void assertVoidAllowed(Booking booking, Invoice invoice) {
        BigDecimal membershipFee = feeAmount(invoice.getMembershipFeeAmount());
        if (membershipFee.compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("error.invoice.membershipSold");
        }
        scratchCardRepository
                .findTopByTenantIdAndBookingIdAndStatus(
                        booking.getTenantId(), booking.getId(), ScratchCardStatus.REDEEMED)
                .ifPresent(c -> {
                    throw new BadRequestException("error.invoice.scratchRedeemed");
                });
    }

    private void syncInvoiceFromBooking(Booking booking, Invoice invoice, List<BookingLineItem> lines) {
        BillPreviewResponse bill = billPreviewFor(booking, lines);

        BigDecimal membershipFee = feeAmount(invoice.getMembershipFeeAmount());
        BigDecimal packageFee = feeAmount(invoice.getPackageFeeAmount());

        BigDecimal oldGrand = invoice.getGrandTotal() != null ? invoice.getGrandTotal() : BigDecimal.ZERO;

        invoice.setSubtotal(bill.getSubtotal());
        invoice.setDiscountAmount(bill.getDiscountAmount());
        invoice.setMembershipDiscountAmount(
                bill.getMembershipDiscountAmount() != null ? bill.getMembershipDiscountAmount() : BigDecimal.ZERO);
        invoice.setPromoDiscountAmount(
                bill.getPromoDiscountAmount() != null ? bill.getPromoDiscountAmount() : BigDecimal.ZERO);
        invoice.setMembershipLabel(bill.getMembershipLabel());
        invoice.setPromoLabel(bill.getPromoLabel());
        invoice.setTaxableAmount(bill.getTaxableAmount());
        invoice.setCgstAmount(bill.getCgstAmount());
        invoice.setSgstAmount(bill.getSgstAmount());

        BigDecimal grandTotal = bill.getGrandTotal()
                .add(membershipFee)
                .add(packageFee)
                .setScale(2, RoundingMode.HALF_UP);
        invoice.setGrandTotal(grandTotal);
        invoice.setPdfStorageKey(null);
        invoice.setPdfStoredAt(null);
        invoiceRepository.save(invoice);

        List<Payment> payments = paymentRepository.findByBookingId(booking.getId());
        if (!payments.isEmpty()) {
            Payment payment = payments.get(0);
            payment.setAmount(grandTotal);
            paymentRepository.save(payment);
        }

        Customer customer = customerRepository.findById(booking.getCustomerId()).orElseThrow();
        BigDecimal delta = grandTotal.subtract(oldGrand);
        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal spend = customer.getLifetimeSpend() != null ? customer.getLifetimeSpend() : BigDecimal.ZERO;
            customer.setLifetimeSpend(spend.add(delta).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            customerRepository.save(customer);
        }

        booking.setMembershipDiscountAmount(bill.getMembershipDiscountAmount());
        booking.setPromoDiscountAmount(bill.getPromoDiscountAmount());
        bookingRepository.save(booking);
    }

    private BillPreviewResponse billPreviewFor(Booking booking, List<BookingLineItem> lines) {
        BillPreviewResponse bill = gstCalculationService.calculate(booking, lines, promoContextFor(booking));
        return servicePackageService.applyValueCreditToPreview(bill, lines);
    }

    private GstCalculationService.PromoContext promoContextFor(Booking booking) {
        return promoResolutionService.resolveForBooking(
                booking.getTenantId(),
                booking.getBranchId(),
                booking.getCustomerId(),
                booking.getCouponId(),
                booking.getOfferId(),
                null,
                null);
    }

    private static BigDecimal feeAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private InvoiceDetailResponse toDetail(Invoice invoice) {
        InvoiceBillUtils.MembershipFeeView fee = InvoiceBillUtils.resolveMembershipFee(invoice);
        InvoiceBillUtils.PackageFeeView pkg = InvoiceBillUtils.resolvePackageFee(invoice);
        return InvoiceDetailResponse.builder()
                .id(invoice.getId())
                .bookingId(invoice.getBookingId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .subtotal(invoice.getSubtotal())
                .discountAmount(invoice.getDiscountAmount())
                .membershipDiscountAmount(invoice.getMembershipDiscountAmount())
                .promoDiscountAmount(invoice.getPromoDiscountAmount())
                .membershipLabel(invoice.getMembershipLabel())
                .promoLabel(invoice.getPromoLabel())
                .membershipFeeAmount(fee.amount())
                .membershipFeeLabel(fee.label())
                .packageFeeAmount(pkg.amount())
                .packageFeeLabel(pkg.label())
                .taxableAmount(invoice.getTaxableAmount())
                .cgstAmount(invoice.getCgstAmount())
                .sgstAmount(invoice.getSgstAmount())
                .grandTotal(invoice.getGrandTotal())
                .customerName(invoice.getCustomerName())
                .customerPhone(invoice.getCustomerPhone())
                .issuedAt(invoice.getIssuedAt())
                .pdfAvailable(invoice.getPdfStorageKey() != null && !invoice.getPdfStorageKey().isBlank())
                .pdfStoredAt(invoice.getPdfStoredAt())
                .build();
    }
}
