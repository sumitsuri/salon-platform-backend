package com.salonplatform.controller;

import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.repository.InvoiceRepository;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.invoice.InvoiceDetailResponse;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.service.InvoicePdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final InvoicePdfService invoicePdfService;

    @GetMapping
    public ApiResponse<List<Invoice>> list() {
        UUID tenantId = SecurityUtils.requireTenantId();
        return ApiResponse.ok(invoiceRepository.findByTenantIdOrderByIssuedAtDesc(tenantId));
    }

    @GetMapping("/booking/{bookingId}")
    public ApiResponse<InvoiceDetailResponse> byBooking(@PathVariable UUID bookingId) {
        Invoice invoice = invoiceRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        SecurityUtils.assertBranchAccess(invoice.getBranchId());
        return ApiResponse.ok(toDetail(invoice));
    }

    @GetMapping("/{id}")
    public ApiResponse<InvoiceDetailResponse> get(@PathVariable UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        SecurityUtils.assertBranchAccess(invoice.getBranchId());
        return ApiResponse.ok(toDetail(invoice));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        SecurityUtils.assertBranchAccess(invoice.getBranchId());
        byte[] pdf = invoicePdfService.generatePdf(id);
        String filename = "invoice-" + (invoice.getInvoiceNumber() != null
                ? invoice.getInvoiceNumber()
                : id) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private InvoiceDetailResponse toDetail(Invoice invoice) {
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
