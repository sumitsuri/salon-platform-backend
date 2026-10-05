package com.salonplatform.dto.scalpscan;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateScalpScanRequest {
    @NotNull
    private UUID customerId;
}
