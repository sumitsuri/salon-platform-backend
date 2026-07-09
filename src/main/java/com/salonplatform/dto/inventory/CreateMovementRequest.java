package com.salonplatform.dto.inventory;

import com.salonplatform.domain.enums.MovementType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateMovementRequest {
    @NotNull
    private UUID branchId;
    @NotNull
    private UUID productId;
    @NotNull
    private MovementType movementType;
    @NotNull
    private BigDecimal quantity;
    private BigDecimal unitCost;
    @NotNull
    private LocalDate movementDate;
    private String note;
}
