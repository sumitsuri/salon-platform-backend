package com.salonplatform.controller;

import com.salonplatform.exception.BadRequestException;
import com.salonplatform.service.InvoiceAccessTokenService;
import com.salonplatform.service.InvoicePdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/invoices")
@RequiredArgsConstructor
public class PublicInvoiceController {

    private final InvoicePdfService invoicePdfService;
    private final InvoiceAccessTokenService invoiceAccessTokenService;

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable UUID id,
            @RequestParam String token) {
        UUID invoiceId = invoiceAccessTokenService.validateAndGetInvoiceId(token);
        if (!invoiceId.equals(id)) {
            throw new BadRequestException("Invalid invoice access token");
        }
        byte[] pdf = invoicePdfService.generatePdfPublic(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=invoice.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
