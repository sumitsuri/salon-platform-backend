package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SalesLocalityResponse {
    private UUID id;
    private String name;
    private String zone;
}
