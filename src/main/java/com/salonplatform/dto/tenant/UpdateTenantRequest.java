package com.salonplatform.dto.tenant;

import lombok.Data;

@Data
public class UpdateTenantRequest {
    private String name;
    private String logoUrl;
    private String primaryColor;
}
