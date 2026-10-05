package com.salonplatform.service.scalpscan;

import com.salonplatform.domain.enums.ScalpLightMode;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public class ScalpScanImageAnalyzer {

    public ScalpScanImageMetrics analyze(byte[] imageBytes, ScalpLightMode lightMode) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return neutralMetrics();
            }
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
                    double gray = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                    brightnessSum += gray;
                    rednessSum += Math.max(0, (r - (g + b) / 2.0) / 255.0);
                    if (lightMode == ScalpLightMode.UV) {
                        uvSum += (b > r + 25 && gray > 0.45) ? 1 : 0;
                    }
                    int rgbRight = image.getRGB(x + step, y);
                    int rgbDown = image.getRGB(x, y + step);
                    double grayR = luminance(rgbRight);
                    double grayD = luminance(rgbDown);
                    edgeSum += Math.abs(gray - grayR) + Math.abs(gray - grayD);
                    textureSum += Math.abs(gray - grayR) + Math.abs(gray - grayD);
                    count++;
                }
            }
            if (count == 0) {
                return neutralMetrics();
            }
            double meanBrightness = brightnessSum / count;
            double redness = rednessSum / count;
            double texture = textureSum / count;
            double edges = edgeSum / count;
            double uv = lightMode == ScalpLightMode.UV ? uvSum / count : 0;
            if (lightMode == ScalpLightMode.CROSS_POLARIZED) {
                redness *= 1.15;
            }
            return new ScalpScanImageMetrics(meanBrightness, redness, texture, uv, edges);
        } catch (IOException e) {
            return neutralMetrics();
        }
    }

    private static double luminance(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
    }

    private static ScalpScanImageMetrics neutralMetrics() {
        return new ScalpScanImageMetrics(0.5, 0.2, 0.15, 0.05, 0.12);
    }
}
