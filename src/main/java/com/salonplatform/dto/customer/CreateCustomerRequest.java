package com.salonplatform.dto.customer;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCustomerRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String phone;
    private String society;
    private String flatUnit;
    private String notes;
}
