package com.salonplatform.dto.membership;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class MembershipListFilter {
    private UUID branchId;
    /** Matches customer name. */
    private String q;
    /** Matches customer phone (digits, partial). */
    private String phone;
    /** Matches card number (partial). */
    private String card;
    private UUID planId;
    private LocalDate endsBefore;
    private LocalDate endsAfter;
    private int page;
    private int size;
}
