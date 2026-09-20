package com.salonplatform.sales.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MapIngestionSummary {
    private int areasProcessed;
    private int leadsInserted;
    private int leadsSkippedDuplicate;
    private int areaErrors;
}
