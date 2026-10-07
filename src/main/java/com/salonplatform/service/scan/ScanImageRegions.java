package com.salonplatform.service.scan;

import com.salonplatform.domain.enums.FaceCaptureZone;
import com.salonplatform.domain.enums.ScalpCaptureZone;

import java.awt.image.BufferedImage;

/** Normalized ROI crops so metrics focus on the tagged zone, not the whole frame. */
public final class ScanImageRegions {

    private ScanImageRegions() {}

    public static BufferedImage cropForScalp(BufferedImage image, ScalpCaptureZone zone) {
        return crop(image, switch (zone) {
            case CROWN -> rect(0.25, 0.2, 0.75, 0.65);
            case HAIRLINE -> rect(0.15, 0.02, 0.85, 0.38);
            case PARTING -> rect(0.38, 0.1, 0.62, 0.75);
            case LEFT_TEMPLE -> rect(0.02, 0.08, 0.38, 0.55);
            case RIGHT_TEMPLE -> rect(0.62, 0.08, 0.98, 0.55);
        });
    }

    public static BufferedImage cropForFace(BufferedImage image, FaceCaptureZone zone) {
        return crop(image, switch (zone) {
            case FULL_FACE -> rect(0.08, 0.06, 0.92, 0.94);
            case FOREHEAD -> rect(0.12, 0.04, 0.88, 0.38);
            case LEFT_CHEEK -> rect(0.04, 0.32, 0.48, 0.72);
            case RIGHT_CHEEK -> rect(0.52, 0.32, 0.96, 0.72);
            case NOSE -> rect(0.36, 0.28, 0.64, 0.62);
            case CHIN -> rect(0.2, 0.62, 0.8, 0.96);
        });
    }

    private record NormRect(double x0, double y0, double x1, double y1) {}

    private static NormRect rect(double x0, double y0, double x1, double y1) {
        return new NormRect(x0, y0, x1, y1);
    }

    private static BufferedImage crop(BufferedImage image, NormRect r) {
        int w = image.getWidth();
        int h = image.getHeight();
        int x = clamp((int) Math.floor(r.x0 * w), 0, w - 2);
        int y = clamp((int) Math.floor(r.y0 * h), 0, h - 2);
        int x2 = clamp((int) Math.ceil(r.x1 * w), x + 1, w);
        int y2 = clamp((int) Math.ceil(r.y1 * h), y + 1, h);
        return image.getSubimage(x, y, x2 - x, y2 - y);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
