package com.salonplatform.dto.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SetServiceBranchesRequest {
    @NotNull
    @Valid
    private List<ServiceBranchPriceRequest> assignments = new ArrayList<>();
}
