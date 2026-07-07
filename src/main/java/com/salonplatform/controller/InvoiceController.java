package com.salonplatform.controller;

import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.repository.InvoiceRepository;
import com.salonplatform.dto.ApiResponse;
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
    public ApiResponse<Invoice> byBooking(@PathVariable UUID bookingId) {
        return ApiResponse.ok(invoiceRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found")));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] pdf = invoicePdfService.generatePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
