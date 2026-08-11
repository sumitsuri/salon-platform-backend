package com.salonplatform.dto.customer;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCustomerRequest {
    @NotBlank
    private String name;
    /** Required when branch {@code phoneNumberRequired} is true. */
    private String phone;
    /** Branch where registration happens — drives phone requirement validation. */
    private UUID branchId;
    private String society;
    private String flatUnit;
    private String notes;
}
