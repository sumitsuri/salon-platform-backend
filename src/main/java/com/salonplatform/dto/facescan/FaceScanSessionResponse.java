package com.salonplatform.dto.facescan;

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
public class FaceScanSessionResponse {
    private UUID id;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private UUID branchId;
    private String status;
    private Integer skinHealthScore;
    private String primaryConcernCode;
    private String staffNotes;
    private Instant createdAt;
    private Instant analyzedAt;
    private int captureCount;
    private FaceScanReportDto report;
    private List<FaceScanCaptureDto> captures;
}
