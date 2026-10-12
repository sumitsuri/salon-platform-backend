package com.salonplatform.dto.advance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Approve (all fields optional — defaults to what was asked) or reject (only {@code note} is read). */
@Data
public class DecideAdvanceRequest {
    @DecimalMin("1")
    private BigDecimal approvedAmount;
    @Min(1)
    @Max(24)
    private Integer installments;
    /** Any date in the first salary month to deduct from; normalised to the 1st. Defaults to next month. */
    private LocalDate firstDeductionMonth;
    @Size(max = 500)
    private String note;
}
