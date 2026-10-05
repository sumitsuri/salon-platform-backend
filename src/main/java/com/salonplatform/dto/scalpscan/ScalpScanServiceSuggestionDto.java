package com.salonplatform.dto.scalpscan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalpScanServiceSuggestionDto {
    private UUID branchServiceId;
    private String name;
    private String reason;
}
