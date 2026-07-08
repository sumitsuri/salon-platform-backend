package com.salonplatform.dto.staff;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class StaffTargetTrendPoint {
    private LocalDate date;
    /** Cumulative actual sales till this date */
    private BigDecimal actualCumulative;
    /** Cumulative ideal pace till this date */
    private BigDecimal idealCumulative;
    /** actualCumulative - idealCumulative */
    private BigDecimal gap;
}
