package com.salonplatform.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateVendorRequest {
    @NotBlank
    private String name;
    private String contactPhone;
    private String contactEmail;
    private String notes;
}
