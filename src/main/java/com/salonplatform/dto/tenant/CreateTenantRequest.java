package com.salonplatform.dto.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTenantRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String slug;
    private String primaryColor;
    @NotBlank
    private String adminName;
    @NotBlank @Email
    private String adminEmail;
    @NotBlank
    private String adminPassword;
}
