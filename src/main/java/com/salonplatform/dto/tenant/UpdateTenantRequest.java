package com.salonplatform.dto.tenant;

import lombok.Data;

@Data
public class UpdateTenantRequest {
    private String name;
    private String logoUrl;
    private String primaryColor;
    /** When true, GST from service catalog applies on bills (default false). */
    private Boolean gstEnabled;
}
