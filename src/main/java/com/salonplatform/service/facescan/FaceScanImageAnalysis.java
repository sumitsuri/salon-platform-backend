package com.salonplatform.service.facescan;

import com.salonplatform.service.scan.ScanCaptureQuality;

public record FaceScanImageAnalysis(FaceScanImageMetrics metrics, ScanCaptureQuality quality) {}
