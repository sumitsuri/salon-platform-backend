package com.salonplatform.dto.booking;

import com.salonplatform.domain.enums.BookingStatus;
import com.salonplatform.domain.enums.DiscountType;
import com.salonplatform.dto.billing.BillPreviewResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BookingResponse {
    private UUID id;
    private UUID branchId;
    private String branchName;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private BookingStatus status;
    private List<BookingLineResponse> lines;
    private DiscountType billDiscountType;
    private BigDecimal billDiscountValue;
    private String billDiscountNote;
    private String notes;
    private BillPreviewResponse billPreview;
    private Instant createdAt;
    private Instant completedAt;
    private UUID invoiceId;
    private Boolean receiptQueued;
}
