package com.salonplatform.dto.scratch;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RedeemScratchCardRequest {
    @NotNull
    private UUID bookingId;
    private String redemptionCode;
    private UUID cardId;
}
