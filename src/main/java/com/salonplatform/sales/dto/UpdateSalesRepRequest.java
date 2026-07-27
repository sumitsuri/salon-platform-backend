package com.salonplatform.sales.dto;

import lombok.Data;

@Data
public class UpdateSalesRepRequest {
    private String name;
    private String email;
    /** Optional password reset. */
    private String password;
    private Boolean active;
}
