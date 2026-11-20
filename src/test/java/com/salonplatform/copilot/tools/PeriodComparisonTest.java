package com.salonplatform.copilot.tools;

import com.salonplatform.dto.analytics.BranchStats;
import com.salonplatform.dto.analytics.DashboardResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodComparisonTest {

    private static final UUID INDIRANAGAR = UUID.randomUUID();
    private static final UUID KORAMANGALA = UUID.randomUUID();

    private static BranchStats branch(UUID id, String name, long revenue, long visits) {
        return BranchStats.builder().branchId(id).branchName(name)
                .revenue(BigDecimal.valueOf(revenue)).visits(visits).build();
    }

    private static DashboardResponse dashboard(long revenue, long visits, BranchStats... branches) {
        return DashboardResponse.builder().totalRevenue(BigDecimal.valueOf(revenue)).totalVisits(visits)
                .branchStats(List.of(branches)).build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("byBranchWorstFirst");
    }

    @Test
    void visitsEffectAndTicketEffectAddUpToTheTotalChange() {
        // 100 visits at 1,000 -> 80 visits at 1,100: revenue 100,000 -> 88,000
        Map<String, Object> result = PeriodComparison.compare(
                dashboard(100_000, 100, branch(INDIRANAGAR, "Indiranagar", 100_000, 100)),
                dashboard(88_000, 80, branch(INDIRANAGAR, "Indiranagar", 88_000, 80)));

        Map<String, Object> row = rows(result).get(0);
        assertThat(row.get("revenueChange")).isEqualTo(-12_000L);
        assertThat(row.get("visitsEffect")).isEqualTo(-20_000L);   // 20 fewer visits x 1,000
        assertThat(row.get("ticketEffect")).isEqualTo(8_000L);     // 100 more per visit x 80 visits
        assertThat((long) row.get("visitsEffect") + (long) row.get("ticketEffect"))
                .isEqualTo((long) row.get("revenueChange"));
        assertThat(row.get("changePercent")).isEqualTo(-12.0);
    }

    @Test
    void worstBranchComesFirst() {
        Map<String, Object> result = PeriodComparison.compare(
                dashboard(200_000, 200, branch(INDIRANAGAR, "Indiranagar", 100_000, 100), branch(KORAMANGALA, "Koramangala", 100_000, 100)),
                dashboard(190_000, 190, branch(INDIRANAGAR, "Indiranagar", 110_000, 110), branch(KORAMANGALA, "Koramangala", 80_000, 80)));

        assertThat(rows(result)).extracting(r -> r.get("name")).containsExactly("Koramangala", "Indiranagar");
    }

    @Test
    void aBranchPresentInOnlyOnePeriodCountsFromZero() {
        Map<String, Object> result = PeriodComparison.compare(
                dashboard(0, 0),
                dashboard(50_000, 50, branch(KORAMANGALA, "Koramangala", 50_000, 50)));

        Map<String, Object> row = rows(result).get(0);
        assertThat(row.get("revenueBefore")).isEqualTo(0L);
        assertThat(row.get("revenueChange")).isEqualTo(50_000L);
        assertThat(row.get("changePercent")).isNull();
        assertThat((long) row.get("visitsEffect") + (long) row.get("ticketEffect")).isEqualTo(50_000L);
    }

    @Test
    void aBranchThatStoppedTradingIsAllVisitsEffect() {
        Map<String, Object> result = PeriodComparison.compare(
                dashboard(60_000, 60, branch(KORAMANGALA, "Koramangala", 60_000, 60)),
                dashboard(0, 0));

        Map<String, Object> row = rows(result).get(0);
        assertThat(row.get("revenueChange")).isEqualTo(-60_000L);
        assertThat(row.get("visitsEffect")).isEqualTo(-60_000L);
        assertThat(row.get("ticketEffect")).isEqualTo(0L);
    }
}
