package com.salonplatform.dto.advance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Owner edits an advance after it has left the pending queue. */
@Data
public class UpdateAdvanceRequest {
    @Size(max = 500)
    private String reason;

    @Size(max = 500)
    private String note;

    @DecimalMin("1")
    private BigDecimal approvedAmount;

    @Min(1)
    @Max(24)
    private Integer installments;

    private LocalDate firstDeductionMonth;

    /** Waive every remaining scheduled installment and mark the advance cancelled. */
    private Boolean revoke;
}
