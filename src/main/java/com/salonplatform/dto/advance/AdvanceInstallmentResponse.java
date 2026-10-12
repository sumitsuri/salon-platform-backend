package com.salonplatform.dto.advance;

import com.salonplatform.domain.enums.InstallmentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class AdvanceInstallmentResponse {
    private UUID id;
    private UUID advanceId;
    private LocalDate dueMonth;
    private BigDecimal amount;
    private InstallmentStatus status;
    private Instant settledAt;
    private String note;
}
