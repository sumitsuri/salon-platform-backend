package com.salonplatform.service.scalpscan;

import com.salonplatform.service.scan.ScanCaptureQuality;

public record ScalpScanImageAnalysis(ScalpScanImageMetrics metrics, ScanCaptureQuality quality) {}
