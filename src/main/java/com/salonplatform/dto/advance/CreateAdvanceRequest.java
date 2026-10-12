package com.salonplatform.dto.advance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAdvanceRequest {
    @NotNull
    @DecimalMin("1")
    private BigDecimal amount;
    @Min(1)
    @Max(24)
    private int installments;
    @NotBlank
    @Size(max = 500)
    private String reason;
}
