package com.salonplatform.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateLocaleRequest {
    @NotBlank
    @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$", message = "Locale must be BCP 47 format e.g. hi-IN")
    private String locale;
}
