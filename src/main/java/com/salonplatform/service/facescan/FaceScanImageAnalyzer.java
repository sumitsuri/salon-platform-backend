package com.salonplatform.service.facescan;

import com.salonplatform.domain.enums.FaceLightMode;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public class FaceScanImageAnalyzer {

    public FaceScanImageMetrics analyze(byte[] imageBytes, FaceLightMode lightMode) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return neutral();
            }
            int w = image.getWidth();
            int h = image.getHeight();
            int step = Math.max(1, Math.min(w, h) / 120);
            long count = 0;
            double brightnessSum = 0;
            double rednessSum = 0;
            double textureSum = 0;
            double uvSum = 0;

            for (int y = step; y < h - step; y += step) {
                for (int x = step; x < w - step; x += step) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    double gray = luminance(rgb);
                    brightnessSum += gray;
                    rednessSum += Math.max(0, (r - (g + b) / 2.0) / 255.0);
                    if (lightMode == FaceLightMode.UV) {
                        uvSum += (b > r + 20 && gray > 0.4) ? 1 : 0;
                    }
                    double grayR = luminance(image.getRGB(x + step, y));
                    double grayD = luminance(image.getRGB(x, y + step));
                    textureSum += Math.abs(gray - grayR) + Math.abs(gray - grayD);
                    count++;
                }
            }
            if (count == 0) return neutral();
            double redness = rednessSum / count;
            if (lightMode == FaceLightMode.CROSS_POLARIZED) {
                redness *= 1.2;
            }
            return new FaceScanImageMetrics(
                    brightnessSum / count,
                    redness,
                    textureSum / count,
                    lightMode == FaceLightMode.UV ? uvSum / count : 0);
        } catch (IOException e) {
            return neutral();
        }
    }

    private static double luminance(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
    }

    private static FaceScanImageMetrics neutral() {
        return new FaceScanImageMetrics(0.52, 0.18, 0.14, 0.04);
    }
}
