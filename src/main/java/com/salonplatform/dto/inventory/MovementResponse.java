package com.salonplatform.dto.inventory;

import com.salonplatform.domain.enums.MovementType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class MovementResponse {
    private UUID id;
    private UUID branchId;
    private String branchName;
    private UUID productId;
    private String productName;
    private String sku;
    private UUID vendorId;
    private String vendorName;
    private MovementType movementType;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private LocalDate movementDate;
    private String note;
    private String recordedByName;
    private Instant createdAt;
}
