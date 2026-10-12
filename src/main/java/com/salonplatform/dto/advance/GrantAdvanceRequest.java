package com.salonplatform.dto.advance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Owner records an advance for an employee without a prior staff-app request. */
@Data
public class GrantAdvanceRequest {
    @NotNull
    private UUID staffId;

    @NotNull
    @DecimalMin("1")
    private BigDecimal amount;

    @Min(1)
    @Max(24)
    private int installments;

    /** Any date in the first salary month to deduct from; normalised to the 1st. Defaults to next month. */
    private LocalDate firstDeductionMonth;

    /** Shown to the employee on their advance history. */
    @Size(max = 500)
    private String reason;

    /** Optional note stored with the decision (also visible to the employee). */
    @Size(max = 500)
    private String note;
}
