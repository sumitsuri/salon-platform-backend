package com.salonplatform.dto.inventory;

import lombok.Data;

@Data
public class UpdateVendorRequest {
    private String name;
    private String contactPhone;
    private String contactEmail;
    private String notes;
}
