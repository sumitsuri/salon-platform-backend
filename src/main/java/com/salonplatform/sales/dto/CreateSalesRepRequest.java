package com.salonplatform.sales.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSalesRepRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String email;
    @NotBlank
    private String password;
}
