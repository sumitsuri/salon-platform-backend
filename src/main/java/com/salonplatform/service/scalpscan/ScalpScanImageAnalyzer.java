package com.salonplatform.service.scalpscan;

import com.salonplatform.domain.enums.ScalpCaptureZone;
import com.salonplatform.domain.enums.ScalpLightMode;
import com.salonplatform.service.scan.ScanCaptureQuality;
import com.salonplatform.service.scan.ScanImageRegions;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public class ScalpScanImageAnalyzer {

    public ScalpScanImageAnalysis analyze(byte[] imageBytes, ScalpLightMode lightMode, ScalpCaptureZone zone) {
        try {
            BufferedImage raw = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (raw == null) {
                return neutralAnalysis();
            }
            BufferedImage image = ScanImageRegions.cropForScalp(raw, zone);
            return analyzeCropped(image, lightMode);
        } catch (IOException e) {
            return neutralAnalysis();
        }
    }

    /** @deprecated use zone-aware overload */
    public ScalpScanImageMetrics analyze(byte[] imageBytes, ScalpLightMode lightMode) {
        return analyze(imageBytes, lightMode, ScalpCaptureZone.CROWN).metrics();
    }

    private ScalpScanImageAnalysis analyzeCropped(BufferedImage image, ScalpLightMode lightMode) {
        int w = image.getWidth();
        int h = image.getHeight();
        int step = Math.max(1, Math.min(w, h) / 120);
        long count = 0;
        double brightnessSum = 0;
        double rednessSum = 0;
        double textureSum = 0;
        double uvSum = 0;
        double edgeSum = 0;

        for (int y = step; y < h - step; y += step) {
            for (int x = step; x < w - step; x += step) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                double gray = luminance(rgb);
                brightnessSum += gray;
                rednessSum += Math.max(0, (r - (g + b) / 2.0) / 255.0);
                if (lightMode == ScalpLightMode.UV) {
                    uvSum += (b > r + 25 && gray > 0.45) ? 1 : 0;
                }
                double grayR = luminance(image.getRGB(x + step, y));
                double grayD = luminance(image.getRGB(x, y + step));
                double edge = Math.abs(gray - grayR) + Math.abs(gray - grayD);
                edgeSum += edge;
                textureSum += edge;
                count++;
            }
        }
        if (count == 0) {
            return neutralAnalysis();
        }
        double meanBrightness = brightnessSum / count;
        double redness = rednessSum / count;
        double texture = textureSum / count;
        double edges = edgeSum / count;
        double uv = lightMode == ScalpLightMode.UV ? uvSum / count : 0;
        if (lightMode == ScalpLightMode.CROSS_POLARIZED) {
            redness *= 1.15;
        }
        double sharpness = edges / 2.0;
        ScanCaptureQuality quality = ScanCaptureQuality.assess(meanBrightness, sharpness);
        return new ScalpScanImageAnalysis(
                new ScalpScanImageMetrics(meanBrightness, redness, texture, uv, edges),
                quality);
    }

    private static double luminance(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
    }

    private static ScalpScanImageAnalysis neutralAnalysis() {
        return new ScalpScanImageAnalysis(
                new ScalpScanImageMetrics(0.5, 0.2, 0.15, 0.05, 0.12),
                ScanCaptureQuality.assess(0.5, 0.02));
    }
}
