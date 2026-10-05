package com.salonplatform.domain.branch;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchReportingTest {

    @Test
    void activeBranchAlwaysOverlaps() {
        Branch b = Branch.builder().status(BranchStatus.ACTIVE).build();
        assertTrue(BranchReporting.overlapsReportingRange(b, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }

    @Test
    void inactiveBranchOverlapsWhenRangeEndsBeforeDeactivationDay() {
        Branch b = Branch.builder()
                .status(BranchStatus.INACTIVE)
                .deactivatedAt(Instant.parse("2026-10-10T12:00:00Z"))
                .build();
        assertTrue(BranchReporting.overlapsReportingRange(b, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10)));
        assertFalse(BranchReporting.overlapsReportingRange(b, LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 31)));
    }
}
