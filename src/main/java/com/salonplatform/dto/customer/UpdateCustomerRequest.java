package com.salonplatform.dto.customer;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateCustomerRequest {
    @NotBlank
    private String name;
}
