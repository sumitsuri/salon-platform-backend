package com.salonplatform.dto.facescan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceScanConcernDto {
    private String code;
    private String label;
    private String severity;
    private int score;
    private String insight;
}
