package com.salonplatform.domain.branch;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** When a branch counts toward dashboards for a calendar date range (soft-deactivation). */
public final class BranchReporting {

    public static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Kolkata");

    private BranchReporting() {}

    /** Last calendar day the branch was operational; null while active. */
    public static LocalDate lastActiveDate(Branch branch) {
        if (branch == null || branch.getStatus() == BranchStatus.ACTIVE) {
            return null;
        }
        Instant at = branch.getDeactivatedAt();
        if (at == null) {
            return null;
        }
        return at.atZone(REPORTING_ZONE).toLocalDate();
    }

    /**
     * True if the branch should appear in reporting for {@code [from, to]} (inclusive).
     * Deactivated branches remain visible for ranges ending on or before their last active day.
     */
    public static boolean overlapsReportingRange(Branch branch, LocalDate from, LocalDate to) {
        if (branch == null || from == null || to == null) {
            return false;
        }
        if (branch.getStatus() == BranchStatus.ACTIVE) {
            return true;
        }
        LocalDate last = lastActiveDate(branch);
        if (last == null) {
            return false;
        }
        return !from.isAfter(last);
    }

    public static java.util.List<Branch> filterForReporting(
            java.util.List<Branch> branches, LocalDate from, LocalDate to) {
        return branches.stream()
                .filter(b -> overlapsReportingRange(b, from, to))
                .toList();
    }

    /** Branches currently open for operations (excludes soft-deactivated). */
    public static java.util.List<Branch> filterOperationallyActive(java.util.List<Branch> branches) {
        return branches.stream()
                .filter(b -> b.getStatus() == BranchStatus.ACTIVE)
                .toList();
    }
}
