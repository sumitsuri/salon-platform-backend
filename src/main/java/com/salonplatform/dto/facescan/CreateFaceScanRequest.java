package com.salonplatform.dto.facescan;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateFaceScanRequest {
    @NotNull
    private UUID customerId;
}
