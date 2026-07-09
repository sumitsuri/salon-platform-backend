package com.salonplatform.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class VendorResponse {
    private UUID id;
    private String name;
    private String contactPhone;
    private String contactEmail;
    private String notes;
    private Instant createdAt;
}
