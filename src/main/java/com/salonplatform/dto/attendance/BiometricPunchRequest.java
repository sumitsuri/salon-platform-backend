package com.salonplatform.dto.attendance;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BiometricPunchRequest {
    @NotBlank
    private String biometricId;
}
