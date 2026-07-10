package com.salonplatform.dto.enquiry;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateEnquiryRequest {
    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 200)
    private String society;

    @NotBlank
    @Email
    @Size(max = 200)
    private String email;

    @NotBlank
    @Size(max = 20)
    private String mobile;

    @NotBlank
    @Size(max = 4000)
    private String message;

    /** Defaults to demo-brand when omitted (Mystic Wellness marketing site). */
    @Size(max = 80)
    private String tenantSlug;
}
