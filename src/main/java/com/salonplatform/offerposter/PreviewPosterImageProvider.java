package com.salonplatform.offerposter;

import com.salonplatform.dto.offerposter.OfferBlockDto;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Local-development stand-in: draws a plain, correctly-worded poster so the whole flow (generate, review, share) can be
 * tested without an image API key. It is not AI art and is labelled "preview" everywhere it is shown.
 */
public class PreviewPosterImageProvider implements PosterImageProvider {

    @Override
    public String name() {
        return "preview";
    }

    @Override
    public String model() {
        return "local-preview";
    }

    @Override
    public GeneratedImage generate(String prompt, OfferContent c) {
        int w = c.format().width();
        int h = c.format().height();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color top;
            Color bottom;
            Color accent = new Color(0xF4D58D);
            switch (c.style()) {
                case ROYAL_PURPLE_GOLD -> { top = new Color(0x2B1239); bottom = new Color(0x5A1E6B); }
                case CREAM_FLORAL -> { top = new Color(0xFBF1E4); bottom = new Color(0xF3D9C4); accent = new Color(0x7A1230); }
                case MODERN_MINIMAL -> { top = new Color(0xFAFAFA); bottom = new Color(0xECECEC); accent = new Color(0xB8860B); }
                default -> { top = new Color(0x4A0E1C); bottom = new Color(0x8B1A2F); }
            }
            boolean light = c.style() == PosterStyle.CREAM_FLORAL || c.style() == PosterStyle.MODERN_MINIMAL;
            Color text = light ? new Color(0x3B0F1B) : Color.WHITE;
            g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g.fillRect(0, 0, w, h);

            int pad = w / 14;
            int y = pad + 20;
            g.setColor(accent);
            y = centered(g, c.brandName().toUpperCase(), w, y, new Font("Serif", Font.PLAIN, w / 24), accent);
            y += 20;
            y = wrapCentered(g, c.headline(), w, pad, y, new Font("Serif", Font.BOLD, w / 11), accent);
            if (c.tagline() != null && !c.tagline().isBlank()) {
                y += 10;
                y = wrapCentered(g, c.tagline(), w, pad, y, new Font("Serif", Font.ITALIC, w / 22), text);
            }
            y += 30;

            for (OfferCopy.Rendered r : OfferCopy.render(c.blocks())) {
                if (y > h - 330) break;
                y = card(g, r, w, pad, y, light, accent);
                y += 22;
            }

            if (c.validUntil() != null && y < h - 260) {
                y = centered(g, "Valid till " + c.validUntil().format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.ENGLISH)),
                        w, y + 10, new Font("SansSerif", Font.BOLD, w / 34), accent);
            }
            int fy = h - 210;
            g.setColor(new Color(0, 0, 0, light ? 20 : 90));
            g.fillRoundRect(pad, fy, w - 2 * pad, 150, 40, 40);
            String where = c.brandName() + (c.address() != null && !c.address().isBlank() ? " — " + c.address() : "");
            Font whereFont = new Font("SansSerif", Font.BOLD, w / 40);
            g.setFont(whereFont);
            int line = fy + 18;
            for (String part : wrap(g.getFontMetrics(), where, w - 2 * pad - 40)) {
                line = centered(g, part, w, line, whereFont, text);
            }
            if (c.phone() != null && !c.phone().isBlank()) {
                centered(g, "Call / WhatsApp to Book  " + c.phone(), w, fy + 100, new Font("SansSerif", Font.BOLD, w / 30), accent);
            }
            centered(g, "PREVIEW — not AI artwork", w, h - 30, new Font("SansSerif", Font.PLAIN, w / 60), text);
        } finally {
            g.dispose();
        }
        return new GeneratedImage(toJpeg(img), "image/jpeg");
    }

    private int card(Graphics2D g, OfferCopy.Rendered r, int w, int pad, int y, boolean light, Color accent) {
        Font head = new Font("Serif", Font.BOLD, w / 26);
        Font body = new Font("SansSerif", Font.PLAIN, w / 31);
        FontMetrics bm = g.getFontMetrics(body);
        int lineH = bm.getHeight() + 6;
        int innerW = w - 2 * pad - 60;
        // Measure first so the card fits its lines.
        int lines = 0;
        for (String line : r.lines()) lines += wrap(bm, line, innerW).size();
        int cardH = 70 + lines * lineH + 20;
        g.setColor(light ? new Color(255, 255, 255, 235) : new Color(255, 248, 235, 235));
        g.fillRoundRect(pad, y, w - 2 * pad, cardH, 44, 44);
        g.setColor(accent);
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(pad, y, w - 2 * pad, cardH, 44, 44);
        g.setFont(head);
        g.setColor(new Color(0x7A1230));
        g.drawString(r.heading(), pad + 30, y + 48);
        g.setFont(body);
        g.setColor(new Color(0x2A2A2A));
        int ly = y + 48 + lineH;
        for (String line : r.lines()) {
            for (String part : wrap(bm, line, innerW)) {
                g.drawString(part, pad + 30, ly);
                ly += lineH;
            }
        }
        return y + cardH;
    }

    private int centered(Graphics2D g, String s, int w, int y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics m = g.getFontMetrics();
        g.drawString(s, Math.max(10, (w - m.stringWidth(s)) / 2), y + m.getAscent());
        return y + m.getHeight();
    }

    private int wrapCentered(Graphics2D g, String s, int w, int pad, int y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics m = g.getFontMetrics();
        for (String line : wrap(m, s, w - 2 * pad)) {
            g.drawString(line, (w - m.stringWidth(line)) / 2, y + m.getAscent());
            y += m.getHeight();
        }
        return y;
    }

    private List<String> wrap(FontMetrics m, String s, int maxWidth) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String trial = cur.isEmpty() ? word : cur + " " + word;
            if (m.stringWidth(trial) > maxWidth && !cur.isEmpty()) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(trial);
            }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    private byte[] toJpeg(BufferedImage img) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.9f);
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(img, null, null), param);
            }
            writer.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new PosterGenerationException("Could not draw the preview poster.", e);
        }
    }
}
