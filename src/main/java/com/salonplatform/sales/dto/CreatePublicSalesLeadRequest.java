package com.salonplatform.sales.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePublicSalesLeadRequest {

    /** Legacy single name field (still accepted from older marketing builds). */
    private String name;

    private String businessName;

    private String contactName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String phone;

    private String city;

    private String branches;

    private String notes;

    @AssertTrue(message = "Provide business and contact name, or a single name")
    public boolean isIdentityPresent() {
        boolean hasPair = isPresent(businessName) && isPresent(contactName);
        boolean hasLegacy = isPresent(name);
        return hasPair || hasLegacy;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
