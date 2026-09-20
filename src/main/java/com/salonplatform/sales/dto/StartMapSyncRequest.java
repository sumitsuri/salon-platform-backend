package com.salonplatform.sales.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class StartMapSyncRequest {

    /** When true, sync every mappable Bangalore area (uses {@link #radiusKm} or default 5). */
    private boolean allAreas;

    /** Required when {@code allAreas} is false. */
    private UUID localityId;

    private Integer radiusKm;
}
