package com.salonplatform.service;

import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.repository.BookingLineItemRepository;
import com.salonplatform.domain.repository.InvoiceRepository;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private final InvoiceRepository invoiceRepository;
    private final BookingLineItemRepository lineItemRepository;

    public byte[] generatePdf(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        SecurityUtils.assertBranchAccess(invoice.getBranchId());

        List<BookingLineItem> lines = lineItemRepository.findByBookingId(invoice.getBookingId());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A5);
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            document.add(new Paragraph("TAX INVOICE", titleFont));
            document.add(new Paragraph("Invoice: " + invoice.getInvoiceNumber(), normalFont));
            document.add(new Paragraph("GSTIN: " + invoice.getBranchGstin(), normalFont));
            document.add(new Paragraph("Date: " + DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm")
                    .format(invoice.getIssuedAt().atZone(ZoneId.of("Asia/Kolkata"))), normalFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Customer: " + invoice.getCustomerName(), normalFont));
            document.add(new Paragraph("Phone: " + invoice.getCustomerPhone(), normalFont));
            if (invoice.getCustomerSociety() != null) {
                document.add(new Paragraph("Society: " + invoice.getCustomerSociety() +
                        (invoice.getCustomerFlat() != null ? "  Flat: " + invoice.getCustomerFlat() : ""), normalFont));
            }
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.addCell(headerCell("Service", smallFont));
            table.addCell(headerCell("Rate", smallFont));
            table.addCell(headerCell("Tax", smallFont));
            table.addCell(headerCell("Amount", smallFont));

            for (BookingLineItem line : lines) {
                table.addCell(cell(line.getServiceName(), smallFont));
                table.addCell(cell(line.getUnitPrice().toString(), smallFont));
                table.addCell(cell(line.getGstRate() + "%", smallFont));
                table.addCell(cell(line.getUnitPrice().toString(), smallFont));
            }
            document.add(table);
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Subtotal: ₹" + invoice.getSubtotal(), normalFont));
            document.add(new Paragraph("Discount: ₹" + invoice.getDiscountAmount(), normalFont));
            document.add(new Paragraph("CGST: ₹" + invoice.getCgstAmount(), normalFont));
            document.add(new Paragraph("SGST: ₹" + invoice.getSgstAmount(), normalFont));
            document.add(new Paragraph("GRAND TOTAL: ₹" + invoice.getGrandTotal(), titleFont));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Thank you! Visit again.", smallFont));

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private PdfPCell headerCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new java.awt.Color(240, 240, 240));
        return cell;
    }

    private PdfPCell cell(String text, Font font) {
        return new PdfPCell(new Phrase(text, font));
    }
}
