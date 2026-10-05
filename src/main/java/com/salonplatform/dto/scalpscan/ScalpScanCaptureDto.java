package com.salonplatform.dto.scalpscan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalpScanCaptureDto {
    private UUID id;
    private String zone;
    private String lightMode;
    private Instant capturedAt;
}
