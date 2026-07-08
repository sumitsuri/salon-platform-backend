package com.salonplatform.dto.booking;

import com.salonplatform.domain.enums.BookingStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class BookingListFilter {
    private UUID branchId;
    private String branch;
    private String customer;
    private String service;
    private String stylist;
    private BookingStatus status;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private int page;
    private int size;
}
