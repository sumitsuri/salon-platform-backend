package com.salonplatform.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Customer;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.repository.BookingLineItemRepository;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.CustomerRepository;
import com.salonplatform.domain.repository.InvoiceRepository;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.util.InvoiceBillUtils;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Premium salon tax invoice — market pattern:
 * solid paper body, photo hero only at top, high-contrast ink, brand as accent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy · hh:mm a");
    private static final String[] TAGLINES = {
            "Look good. Feel better.",
            "Your glow, our craft.",
            "Styled with care. Finished with love.",
            "Where confidence gets a fresh cut.",
            "Beauty rituals, beautifully done.",
            "Crafted for you — visit after visit."
    };
    private static final String[] FOOTER_LINES = {
            "Thank you for choosing us. We can't wait to see you again.",
            "Your chair is always ready. Until next time!",
            "Grateful for your trust. Come back glowing.",
            "Every visit, a little more you. See you soon!"
    };
    private static final String SALON_HEADER_RESOURCE = "/invoice/salon-header.jpg";

    private static final Color PAPER = new Color(250, 247, 242);
    private static final Color SURFACE = new Color(255, 255, 255);
    private static final Color INK = new Color(28, 25, 23);
    private static final Color MUTED = new Color(107, 98, 92);
    private static final Color RULE = new Color(220, 210, 198);
    private static final Color ROW_ALT = new Color(247, 243, 237);
    private static final Color GOLD = new Color(168, 132, 74);
    private static final Color CHARCOAL = new Color(42, 38, 36);

    private static volatile byte[] salonHeaderBytes;

    private final InvoiceRepository invoiceRepository;
    private final BookingLineItemRepository lineItemRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final InvoicePdfStorageService storageService;

    public byte[] generatePdf(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        SecurityUtils.assertBranchAccess(invoice.getBranchId());
        return loadOrBuild(invoice);
    }

    public byte[] generatePdfPublic(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        return loadOrBuild(invoice);
    }

    @Transactional
    public void persistPdf(Invoice invoice) {
        try {
            if (invoice.getIssuedAt() == null) {
                invoice.setIssuedAt(Instant.now());
            }
            byte[] pdf = buildPdf(invoice);
            String key = storageService.store(invoice.getTenantId(), invoice.getId(), invoice.getInvoiceNumber(), pdf);
            invoice.setPdfStorageKey(key);
            invoice.setPdfStoredAt(Instant.now());
            invoiceRepository.save(invoice);
        } catch (Exception e) {
            log.warn("Invoice PDF storage failed for {}: {} — download will regenerate on demand",
                    invoice.getId(), e.toString());
        }
    }

    private byte[] loadOrBuild(Invoice invoice) {
        // Always rebuild so layout updates apply on next download.
        byte[] pdf = buildPdf(invoice);
        try {
            String key = storageService.store(invoice.getTenantId(), invoice.getId(), invoice.getInvoiceNumber(), pdf);
            invoice.setPdfStorageKey(key);
            invoice.setPdfStoredAt(Instant.now());
            invoiceRepository.save(invoice);
        } catch (Exception e) {
            log.warn("Could not backfill invoice PDF storage for {}: {}", invoice.getId(), e.getMessage());
        }
        return pdf;
    }

    private byte[] buildPdf(Invoice invoice) {
        List<BookingLineItem> lines = lineItemRepository.findByBookingId(invoice.getBookingId());
        Tenant tenant = tenantRepository.findById(invoice.getTenantId()).orElse(null);
        Branch branch = branchRepository.findById(invoice.getBranchId()).orElse(null);
        Customer customer = customerRepository.findById(invoice.getCustomerId()).orElse(null);
        String visitPassId = customer != null ? customer.getVisitPassId() : null;

        String brandName = tenant != null && tenant.getName() != null ? tenant.getName() : "Salon";
        String branchName = branch != null && branch.getName() != null ? branch.getName() : "Branch";
        String branchPhone = branch != null ? branch.getPhone() : null;
        String branchAddress = branch != null ? branch.getAddress() : null;
        Color brand = parseColor(tenant != null ? tenant.getPrimaryColor() : null);
        Color brandDark = darken(brand, 0.42f);
        Color accentSoft = soften(brand, 0.92f);

        int vibe = Math.floorMod(invoice.getId() != null ? invoice.getId().hashCode() : brandName.hashCode(), TAGLINES.length);
        String tagline = TAGLINES[vibe];
        String footerLine = FOOTER_LINES[vibe % FOOTER_LINES.length];

        Instant issued = invoice.getIssuedAt() != null ? invoice.getIssuedAt() : Instant.now();
        String issuedLabel = DATE_FMT.format(issued.atZone(IST));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // A5 single-page bill. Side margins for readable body.
            // Critical: never wrap the whole body in one PdfPCell — OpenPDF will
            // push that cell to page 2 if it does not fit under the hero.
            float margin = 16f;
            Document document = new Document(PageSize.A5, margin, margin, 10f, 14f);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PaperCanvasEvent());
            document.open();

            Font whiteBrand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.WHITE);
            Font whiteSmall = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, new Color(235, 230, 224));
            Font whiteTag = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7f, new Color(220, 210, 198));
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7f, GOLD);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, INK);
            Font bodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, INK);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, INK);
            Font smallMuted = FontFactory.getFont(FontFactory.HELVETICA, 7f, MUTED);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 7f, MUTED);
            Font tableHeadFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7f, Color.WHITE);
            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, Color.WHITE);
            Font moneyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, INK);
            Font thanksFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7.5f, MUTED);

            float contentWidth = document.getPageSize().getWidth() - (margin * 2);
            float heroHeight = 68f;

            Image hero = buildHeroBanner(
                    brandName, branchName, tagline, brand, brandDark,
                    contentWidth, heroHeight);
            if (hero != null) {
                hero.setAlignment(Element.ALIGN_CENTER);
                hero.setSpacingBefore(0f);
                hero.setSpacingAfter(0f);
                document.add(hero);
            } else {
                document.add(fallbackHero(brandName, branchName, tagline, whiteBrand, whiteSmall, whiteTag, brandDark));
            }

            document.add(ruleBand(GOLD, 2f));

            PdfPTable meta = new PdfPTable(new float[]{1.15f, 1f});
            meta.setWidthPercentage(100);
            meta.setSpacingBefore(8f);
            meta.setSpacingAfter(8f);
            meta.setSplitLate(false);
            meta.setKeepTogether(false);

            PdfPCell leftMeta = surfaceCard();
            leftMeta.addElement(new Paragraph("TAX INVOICE", sectionFont));
            leftMeta.addElement(vspace(3f));
            leftMeta.addElement(new Paragraph(invoice.getInvoiceNumber(), bodyBold));
            leftMeta.addElement(new Paragraph(issuedLabel, smallMuted));
            String gstin = invoice.getBranchGstin() != null ? invoice.getBranchGstin() : "—";
            leftMeta.addElement(new Paragraph("GSTIN  " + gstin, smallFont));
            if (branchPhone != null && !branchPhone.isBlank()) {
                leftMeta.addElement(new Paragraph("Phone  " + branchPhone, smallFont));
            }
            if (branchAddress != null && !branchAddress.isBlank()) {
                leftMeta.addElement(new Paragraph(branchAddress, smallMuted));
            }
            meta.addCell(leftMeta);

            PdfPCell rightMeta = surfaceCard();
            rightMeta.addElement(new Paragraph("BILLED TO", sectionFont));
            rightMeta.addElement(vspace(3f));
            rightMeta.addElement(new Paragraph(nullSafe(invoice.getCustomerName()), bodyBold));
            String phoneLine = invoice.getCustomerPhone() != null && !invoice.getCustomerPhone().isBlank()
                    ? invoice.getCustomerPhone()
                    : null;
            if (phoneLine != null) {
                rightMeta.addElement(new Paragraph(phoneLine, smallFont));
            }
            if (visitPassId != null && !visitPassId.isBlank()) {
                rightMeta.addElement(new Paragraph("Visit Pass  " + visitPassId, smallFont));
            }
            if (invoice.getCustomerSociety() != null && !invoice.getCustomerSociety().isBlank()) {
                String loc = invoice.getCustomerSociety();
                if (invoice.getCustomerFlat() != null && !invoice.getCustomerFlat().isBlank()) {
                    loc = loc + " · Flat " + invoice.getCustomerFlat();
                }
                rightMeta.addElement(new Paragraph(loc, smallMuted));
            }
            meta.addCell(rightMeta);
            document.add(meta);

            Paragraph servicesTitle = new Paragraph("SERVICES", sectionFont);
            servicesTitle.setSpacingBefore(0f);
            servicesTitle.setSpacingAfter(4f);
            document.add(servicesTitle);

            PdfPTable table = new PdfPTable(new float[]{3.4f, 1.15f, 1.15f});
            table.setWidthPercentage(100);
            table.setSpacingAfter(6f);
            table.setSplitLate(false);
            table.setHeaderRows(1);
            table.addCell(headCell("#  Service", tableHeadFont, CHARCOAL));
            table.addCell(headCell("Rate", tableHeadFont, CHARCOAL, Element.ALIGN_RIGHT));
            table.addCell(headCell("Amount", tableHeadFont, CHARCOAL, Element.ALIGN_RIGHT));

            boolean alt = false;
            int idx = 1;
            var membershipFee = InvoiceBillUtils.resolveMembershipFee(invoice);
            for (BookingLineItem line : lines) {
                Color bg = alt ? ROW_ALT : SURFACE;
                table.addCell(bodyCell(idx + "  " + nullSafe(line.getServiceName()), smallFont, bg, Element.ALIGN_LEFT));
                table.addCell(bodyCell(money(line.getUnitPrice()), smallFont, bg, Element.ALIGN_RIGHT));
                table.addCell(bodyCell(money(line.getUnitPrice()), smallFont, bg, Element.ALIGN_RIGHT));
                alt = !alt;
                idx++;
            }
            if (membershipFee.amount().compareTo(BigDecimal.ZERO) > 0) {
                Color bg = alt ? ROW_ALT : SURFACE;
                String feeName = membershipFee.label() != null ? membershipFee.label() : "Membership card";
                table.addCell(bodyCell(idx + "  " + feeName, smallFont, bg, Element.ALIGN_LEFT));
                table.addCell(bodyCell(money(membershipFee.amount()), smallFont, bg, Element.ALIGN_RIGHT));
                table.addCell(bodyCell(money(membershipFee.amount()), smallFont, bg, Element.ALIGN_RIGHT));
            }
            document.add(table);

            PdfPTable totalsWrap = new PdfPTable(new float[]{0.85f, 1.15f});
            totalsWrap.setWidthPercentage(100);
            totalsWrap.setSpacingAfter(4f);
            totalsWrap.setSplitLate(false);
            totalsWrap.setKeepTogether(true);
            totalsWrap.addCell(emptyCell());

            PdfPCell totalsCard = new PdfPCell();
            totalsCard.setBorder(Rectangle.NO_BORDER);
            totalsCard.setBackgroundColor(SURFACE);
            totalsCard.setPadding(6f);
            totalsCard.setPaddingTop(4f);
            totalsCard.setBorderWidthTop(1.2f);
            totalsCard.setBorderColorTop(GOLD);

            PdfPTable totals = new PdfPTable(new float[]{1.5f, 1f});
            totals.setWidthPercentage(100);
            addTotalRow(totals, "Subtotal", money(invoice.getSubtotal()), labelFont, moneyBold, SURFACE);
            if (invoice.getMembershipDiscountAmount() != null
                    && invoice.getMembershipDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                String label = invoice.getMembershipLabel() != null ? invoice.getMembershipLabel() : "Membership";
                addTotalRow(totals, label, "−" + money(invoice.getMembershipDiscountAmount()),
                        labelFont, moneyBold, accentSoft);
            }
            if (invoice.getPromoDiscountAmount() != null
                    && invoice.getPromoDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                String label = invoice.getPromoLabel() != null ? invoice.getPromoLabel() : "Promo";
                addTotalRow(totals, label, "−" + money(invoice.getPromoDiscountAmount()),
                        labelFont, moneyBold, accentSoft);
            } else if (invoice.getDiscountAmount() != null
                    && invoice.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                    && (invoice.getMembershipDiscountAmount() == null
                    || invoice.getMembershipDiscountAmount().compareTo(BigDecimal.ZERO) == 0)
                    && (invoice.getPromoDiscountAmount() == null
                    || invoice.getPromoDiscountAmount().compareTo(BigDecimal.ZERO) == 0)) {
                addTotalRow(totals, "Discount", "−" + money(invoice.getDiscountAmount()),
                        labelFont, moneyBold, accentSoft);
            }
            addTotalRow(totals, "CGST", money(invoice.getCgstAmount()), labelFont, bodyFont, SURFACE);
            addTotalRow(totals, "SGST", money(invoice.getSgstAmount()), labelFont, bodyFont, SURFACE);
            if (membershipFee.amount().compareTo(BigDecimal.ZERO) > 0) {
                String feeLabel = membershipFee.label() != null ? membershipFee.label() : "Member card";
                addTotalRow(totals, feeLabel, money(membershipFee.amount()), labelFont, moneyBold, SURFACE);
            }
            totalsCard.addElement(totals);

            PdfPTable grand = new PdfPTable(new float[]{1.4f, 1f});
            grand.setWidthPercentage(100);
            grand.setSpacingBefore(3f);
            PdfPCell gtLabel = new PdfPCell(new Phrase("AMOUNT PAYABLE", totalFont));
            gtLabel.setBackgroundColor(CHARCOAL);
            gtLabel.setBorder(Rectangle.NO_BORDER);
            gtLabel.setPadding(7f);
            PdfPCell gtValue = new PdfPCell(new Phrase(money(invoice.getGrandTotal()), totalFont));
            gtValue.setBackgroundColor(CHARCOAL);
            gtValue.setBorder(Rectangle.NO_BORDER);
            gtValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            gtValue.setPadding(7f);
            grand.addCell(gtLabel);
            grand.addCell(gtValue);
            totalsCard.addElement(grand);
            totalsWrap.addCell(totalsCard);
            document.add(totalsWrap);

            document.add(ruleBand(RULE, 0.7f));

            Paragraph thanks = new Paragraph(footerLine, thanksFont);
            thanks.setAlignment(Element.ALIGN_CENTER);
            thanks.setSpacingBefore(6f);
            thanks.setSpacingAfter(2f);
            document.add(thanks);

            if (visitPassId != null && !visitPassId.isBlank()) {
                Paragraph passLine = new Paragraph("Your Visit Pass: " + visitPassId + " — show on your next visit for offers & membership.", smallMuted);
                passLine.setAlignment(Element.ALIGN_CENTER);
                passLine.setSpacingAfter(4f);
                document.add(passLine);
            }

            Paragraph powered = new Paragraph(brandName + "  ·  " + branchName, smallMuted);
            powered.setAlignment(Element.ALIGN_CENTER);
            powered.setSpacingAfter(2f);
            document.add(powered);

            Image mark = resolveLogo(tenant, brandName, brand, brandDark);
            if (mark != null) {
                mark.scaleToFit(22, 22);
                mark.setAlignment(Element.ALIGN_CENTER);
                PdfPTable markTable = new PdfPTable(1);
                markTable.setWidthPercentage(100);
                markTable.setSpacingBefore(2f);
                PdfPCell mc = new PdfPCell(mark, false);
                mc.setBorder(Rectangle.NO_BORDER);
                mc.setHorizontalAlignment(Element.ALIGN_CENTER);
                mc.setPaddingTop(2f);
                markTable.addCell(mc);
                document.add(markTable);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private Image buildHeroBanner(String brandName, String branchName, String tagline,
                                  Color brand, Color brandDark, float pageWidthPt, float heightPt) {
        try {
            byte[] bytes = loadSalonHeader();
            int w = Math.round(pageWidthPt * 2.2f); // retina-ish raster
            int h = Math.round(heightPt * 2.2f);
            BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (bytes != null) {
                BufferedImage photo = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if (photo != null) {
                    // Cover-crop
                    double scale = Math.max((double) w / photo.getWidth(), (double) h / photo.getHeight());
                    int dw = (int) Math.round(photo.getWidth() * scale);
                    int dh = (int) Math.round(photo.getHeight() * scale);
                    int dx = (w - dw) / 2;
                    int dy = (h - dh) / 2;
                    g.drawImage(photo, dx, dy, dw, dh, null);
                } else {
                    fillHeroFallback(g, w, h, brandDark);
                }
            } else {
                fillHeroFallback(g, w, h, brandDark);
            }

            // Dark cinematic gradient for white type contrast
            g.setPaint(new LinearGradientPaint(
                    new Point2D.Float(0, 0),
                    new Point2D.Float(0, h),
                    new float[]{0f, 0.35f, 1f},
                    new Color[]{
                            new Color(20, 16, 14, 70),
                            new Color(20, 16, 14, 120),
                            new Color(18, 14, 12, 210)
                    }));
            g.fillRect(0, 0, w, h);

            // Left brand accent bar
            g.setColor(new Color(brand.getRed(), brand.getGreen(), brand.getBlue(), 220));
            g.fillRect(0, 0, Math.max(8, w / 90), h);
            g.setColor(GOLD);
            g.fillRect(Math.max(8, w / 90), 0, Math.max(4, w / 180), h);

            // Monogram chip
            int chip = Math.round(h * 0.42f);
            int chipX = Math.round(w * 0.055f);
            int chipY = (h - chip) / 2;
            g.setColor(new Color(255, 255, 255, 28));
            g.fillRoundRect(chipX - 4, chipY - 4, chip + 8, chip + 8, 18, 18);
            g.setColor(brandDark);
            g.fillRoundRect(chipX, chipY, chip, chip, 16, 16);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(2.2f));
            g.drawRoundRect(chipX + 3, chipY + 3, chip - 6, chip - 6, 12, 12);
            String initials = initials(brandName);
            g.setColor(Color.WHITE);
            java.awt.Font initFont = new java.awt.Font("SansSerif", java.awt.Font.BOLD, initials.length() > 2 ? chip / 3 : chip / 2);
            g.setFont(initFont);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(initials, chipX + (chip - fm.stringWidth(initials)) / 2,
                    chipY + (chip - fm.getHeight()) / 2 + fm.getAscent());

            // Brand copy
            int textX = chipX + chip + Math.round(w * 0.035f);
            g.setColor(Color.WHITE);
            java.awt.Font titleFont = new java.awt.Font("SansSerif", java.awt.Font.BOLD, Math.round(h * 0.22f));
            g.setFont(titleFont);
            g.drawString(brandName, textX, Math.round(h * 0.40f));

            g.setColor(new Color(235, 228, 218));
            java.awt.Font branchFont = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, Math.round(h * 0.12f));
            g.setFont(branchFont);
            g.drawString(branchName, textX, Math.round(h * 0.58f));

            g.setColor(new Color(210, 198, 180));
            java.awt.Font tagFont = new java.awt.Font("SansSerif", java.awt.Font.ITALIC, Math.round(h * 0.10f));
            g.setFont(tagFont);
            g.drawString(tagline, textX, Math.round(h * 0.74f));

            // TAX INVOICE badge — top right, high contrast
            String badge = "TAX INVOICE";
            java.awt.Font badgeF = new java.awt.Font("SansSerif", java.awt.Font.BOLD, Math.round(h * 0.095f));
            g.setFont(badgeF);
            FontMetrics bfm = g.getFontMetrics();
            int bw = bfm.stringWidth(badge) + Math.round(w * 0.04f);
            int bh = Math.round(h * 0.22f);
            int bx = w - bw - Math.round(w * 0.045f);
            int by = Math.round(h * 0.14f);
            g.setColor(new Color(250, 247, 242));
            g.fillRoundRect(bx, by, bw, bh, 10, 10);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(1.6f));
            g.drawRoundRect(bx, by, bw, bh, 10, 10);
            g.setColor(CHARCOAL);
            g.drawString(badge, bx + (bw - bfm.stringWidth(badge)) / 2, by + (bh - bfm.getHeight()) / 2 + bfm.getAscent());

            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(canvas, "jpg", out);
            Image img = Image.getInstance(out.toByteArray());
            img.scaleAbsolute(pageWidthPt, heightPt);
            return img;
        } catch (Exception e) {
            log.debug("Hero banner build failed: {}", e.getMessage());
            return null;
        }
    }

    private static void fillHeroFallback(Graphics2D g, int w, int h, Color brandDark) {
        g.setPaint(new LinearGradientPaint(
                new Point2D.Float(0, 0),
                new Point2D.Float(w, h),
                new float[]{0f, 1f},
                new Color[]{brandDark, darken(brandDark, 0.25f)}));
        g.fillRect(0, 0, w, h);
    }

    private static PdfPTable fallbackHero(String brandName, String branchName, String tagline,
                                          Font whiteBrand, Font whiteSmall, Font whiteTag, Color brandDark) {
        PdfPTable hero = new PdfPTable(1);
        hero.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(brandDark);
        cell.setPadding(14f);
        cell.setPaddingTop(14f);
        cell.setPaddingBottom(14f);
        cell.addElement(new Paragraph(brandName, whiteBrand));
        cell.addElement(new Paragraph(branchName, whiteSmall));
        cell.addElement(new Paragraph(tagline, whiteTag));
        hero.addCell(cell);
        return hero;
    }

    private static byte[] loadSalonHeader() {
        if (salonHeaderBytes != null) {
            return salonHeaderBytes;
        }
        synchronized (InvoicePdfService.class) {
            if (salonHeaderBytes != null) {
                return salonHeaderBytes;
            }
            try (InputStream in = InvoicePdfService.class.getResourceAsStream(SALON_HEADER_RESOURCE)) {
                if (in == null) {
                    log.warn("Invoice salon header missing: {}", SALON_HEADER_RESOURCE);
                    return null;
                }
                salonHeaderBytes = in.readAllBytes();
                return salonHeaderBytes;
            } catch (Exception e) {
                log.warn("Could not load invoice salon header: {}", e.getMessage());
                return null;
            }
        }
    }

    /** Solid paper + thin gold frame — no photo behind text. */
    private static final class PaperCanvasEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            PdfContentByte canvas = writer.getDirectContentUnder();
            canvas.saveState();
            canvas.setColorFill(PAPER);
            canvas.rectangle(page.getLeft(), page.getBottom(), page.getWidth(), page.getHeight());
            canvas.fill();

            // Subtle gold frame
            canvas.setColorStroke(GOLD);
            canvas.setLineWidth(1.1f);
            float m = 8f;
            canvas.rectangle(page.getLeft() + m, page.getBottom() + m,
                    page.getWidth() - 2 * m, page.getHeight() - 2 * m);
            canvas.stroke();
            canvas.restoreState();
        }
    }

    private Image resolveLogo(Tenant tenant, String brandName, Color brand, Color brandDark) {
        if (tenant != null && tenant.getLogoUrl() != null && !tenant.getLogoUrl().isBlank()) {
            try {
                Image img = Image.getInstance(URI.create(tenant.getLogoUrl().trim()).toURL());
                img.setAlignment(Element.ALIGN_CENTER);
                return img;
            } catch (Exception e) {
                log.debug("Could not load tenant logoUrl for invoice: {}", e.getMessage());
            }
        }
        return monogramLogo(brandName, brand, brandDark);
    }

    private Image monogramLogo(String brandName, Color brand, Color brandDark) {
        try {
            String initials = initials(brandName);
            int size = 96;
            BufferedImage bi = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(brandDark);
            g.fillRoundRect(4, 4, size - 8, size - 8, 22, 22);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(10, 10, size - 20, size - 20, 16, 16);
            g.setColor(Color.WHITE);
            java.awt.Font font = new java.awt.Font("SansSerif", java.awt.Font.BOLD, initials.length() > 2 ? 28 : 36);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int x = (size - fm.stringWidth(initials)) / 2;
            int y = (size - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(initials, x, y);
            g.dispose();
            return Image.getInstance(bi, null);
        } catch (Exception e) {
            log.debug("Monogram logo failed: {}", e.getMessage());
            return null;
        }
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "S";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (sb.length() >= 2) break;
        }
        return sb.length() == 0 ? "S" : sb.toString();
    }

    private static PdfPTable ruleBand(Color color, float height) {
        PdfPTable band = new PdfPTable(1);
        band.setWidthPercentage(100);
        PdfPCell a = new PdfPCell();
        a.setBorder(Rectangle.NO_BORDER);
        a.setBackgroundColor(color);
        a.setFixedHeight(height);
        band.addCell(a);
        return band;
    }

    private static PdfPCell surfaceCard() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(SURFACE);
        cell.setPadding(7f);
        cell.setPaddingRight(8f);
        cell.setBorderWidthBottom(0.6f);
        cell.setBorderColorBottom(RULE);
        return cell;
    }

    private static PdfPCell emptyCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(PAPER);
        return cell;
    }

    private static PdfPCell headCell(String text, Font font, Color bg) {
        return headCell(text, font, bg, Element.ALIGN_LEFT);
    }

    private static PdfPCell headCell(String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(5f);
        cell.setPaddingTop(6f);
        cell.setPaddingBottom(6f);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private static PdfPCell bodyCell(String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBorderWidthBottom(0.5f);
        cell.setBorderColorBottom(RULE);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private static void addTotalRow(PdfPTable totals, String label, String value,
                                    Font labelFont, Font valueFont, Color bg) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setBackgroundColor(bg);
        l.setPadding(3.5f);
        PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setBackgroundColor(bg);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPadding(3.5f);
        totals.addCell(l);
        totals.addCell(v);
    }

    private static Paragraph vspace(float leading) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(leading);
        return p;
    }

    private static String money(BigDecimal amount) {
        BigDecimal v = amount != null ? amount : BigDecimal.ZERO;
        return "₹" + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nullSafe(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static Color parseColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return new Color(15, 118, 110);
        }
        try {
            String h = hex.trim();
            if (h.startsWith("#")) h = h.substring(1);
            if (h.length() == 3) {
                h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            }
            return new Color(Integer.parseInt(h, 16));
        } catch (Exception e) {
            return new Color(15, 118, 110);
        }
    }

    private static Color darken(Color c, float factor) {
        return new Color(
                Math.max(0, (int) (c.getRed() * (1 - factor))),
                Math.max(0, (int) (c.getGreen() * (1 - factor))),
                Math.max(0, (int) (c.getBlue() * (1 - factor)))
        );
    }

    private static Color soften(Color c, float mixTowardWhite) {
        int r = (int) (c.getRed() + (255 - c.getRed()) * mixTowardWhite);
        int g = (int) (c.getGreen() + (255 - c.getGreen()) * mixTowardWhite);
        int b = (int) (c.getBlue() + (255 - c.getBlue()) * mixTowardWhite);
        return new Color(clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
