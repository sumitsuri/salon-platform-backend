package com.salonplatform.dto.scalpscan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalpScanSessionResponse {
    private UUID id;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private UUID branchId;
    private String status;
    private Integer scalpHealthScore;
    private String primaryConcernCode;
    private String staffNotes;
    private Instant createdAt;
    private Instant analyzedAt;
    private int captureCount;
    private ScalpScanReportDto report;
    private List<ScalpScanCaptureDto> captures;
}
