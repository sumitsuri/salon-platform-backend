package com.salonplatform.dto.payment;

import com.salonplatform.domain.enums.PaymentMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class RecordPaymentRequest {
    @NotNull
    private PaymentMode mode;
    @NotNull
    private BigDecimal amount;
    private String reference;
    private List<PaymentSplitRequest> splits;
    /** Optional manager tax amounts; when set (with sgstAmount), must be &gt;= 0. Defaults to calculated GST if omitted. */
    private BigDecimal cgstAmount;
    /** Optional manager tax amounts; when set (with cgstAmount), must be &gt;= 0. Defaults to calculated GST if omitted. */
    private BigDecimal sgstAmount;
}
