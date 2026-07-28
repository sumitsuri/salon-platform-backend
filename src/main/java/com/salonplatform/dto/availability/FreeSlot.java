package com.salonplatform.dto.availability;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FreeSlot {
    private Instant startAt;
    private Instant endAt;
    private Integer minutes;
}
