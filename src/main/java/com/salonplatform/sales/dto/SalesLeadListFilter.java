package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.LeadStage;
import com.salonplatform.sales.domain.enums.LeadSource;
import com.salonplatform.sales.domain.enums.LeadType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class SalesLeadListFilter {
    private LeadStage stage;
    private LeadType leadType;
    private LeadSource source;
    private UUID assignedRepId;
    /** Admin filter — match any of these reps (empty = all reps). */
    private List<UUID> assignedRepIds;
    private UUID localityId;
    /** Resolved from localityId for OR-match on localityName (legacy rows). */
    private String localityName;
    private String search;
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private Boolean mineOnly;
    private int page;
    private int size;
}
