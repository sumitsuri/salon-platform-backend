package com.salonplatform.dto.advance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What to take off each employee's salary for a month, so payroll can be paid net of advances. */
@Data
@Builder
public class DeductionsResponse {
    private LocalDate month;
    private BigDecimal totalDue;
    private BigDecimal totalDeducted;
    private List<Row> rows;

    @Data
    @Builder
    public static class Row {
        private UUID staffId;
        private String staffName;
        private String branchName;
        private BigDecimal baseSalary;
        /** Scheduled (not yet deducted) installments this month. */
        private BigDecimal dueAmount;
        private BigDecimal deductedAmount;
        /** Base salary minus what is still due — before any absence adjustment, which payroll does not automate. */
        private BigDecimal netAfterAdvances;
        private int daysPresent;
        private double approvedLeaveDays;
        private List<AdvanceInstallmentResponse> installments;
    }
}
