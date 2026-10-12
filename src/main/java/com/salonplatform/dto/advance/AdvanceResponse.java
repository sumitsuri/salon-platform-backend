package com.salonplatform.dto.advance;

import com.salonplatform.domain.enums.AdvanceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AdvanceResponse {
    private UUID id;
    private UUID staffId;
    private String staffName;
    private UUID branchId;
    private String branchName;
    private BigDecimal requestedAmount;
    private int requestedInstallments;
    private String reason;
    private AdvanceStatus status;
    private BigDecimal approvedAmount;
    private Integer approvedInstallments;
    private LocalDate firstDeductionMonth;
    private String decisionNote;
    private Instant decidedAt;
    private String decidedByName;
    private Instant createdAt;
    /** Still to be recovered (scheduled installments). */
    private BigDecimal outstanding;
    /** Already recovered or forgiven. */
    private BigDecimal settled;
    private List<AdvanceInstallmentResponse> installments;
    /** Pending requests only: how this employee's earlier requests look, to inform the decision. */
    private EmployeeAdvanceSummary requesterHistory;
}
