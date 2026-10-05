package com.salonplatform.dto.scalpscan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalpScanConcernDto {
    private String code;
    private String label;
    private String severity;
    private int score;
    private String insight;
}
