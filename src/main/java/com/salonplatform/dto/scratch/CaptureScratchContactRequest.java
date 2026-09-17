package com.salonplatform.dto.scratch;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CaptureScratchContactRequest {
    @NotBlank
    private String phone;
    private String name;
}
