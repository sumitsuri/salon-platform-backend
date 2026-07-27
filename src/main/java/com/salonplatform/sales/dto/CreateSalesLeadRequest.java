package com.salonplatform.sales.dto;

import com.salonplatform.sales.domain.enums.LeadSource;
import com.salonplatform.sales.domain.enums.LeadType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class CreateSalesLeadRequest {

    @NotBlank
    private String businessName;

    @NotBlank
    private String contactName;

    private String email;

    @NotBlank
    private String phone;

    @NotNull
    private LeadType leadType;

    private LeadSource source;

    private UUID localityId;

    private String localityName;

    private String address;

    private String city;

    private Integer expectedBranches;

    private String useCase;

    private String notes;

    private UUID assignedRepId;

    private Instant nextFollowUpAt;
}
