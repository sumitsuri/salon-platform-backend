package com.salonplatform.copilot.tools;

import com.salonplatform.dto.analytics.StaffAttendanceSummary;
import com.salonplatform.dto.analytics.StaffSalesPerformanceResponse.StaffSalesRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffScorecardServiceTest {

    private static final UUID ASHA = UUID.randomUUID();
    private static final UUID BILAL = UUID.randomUUID();
    private static final UUID CHITRA = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 10, 25);
    private static final LocalDate END = LocalDate.of(2026, 11, 23);

    private static StaffSalesRow sales(UUID id, String name, long lines, long revenue) {
        return StaffSalesRow.builder().staffId(id).staffName(name).branchName("Koramangala").salesCount(lines)
                .finalRevenue(BigDecimal.valueOf(revenue)).avgFinalTicket(BigDecimal.valueOf(lines == 0 ? 0 : revenue / lines))
                .build();
    }

    private static StaffAttendanceSummary att(UUID id, String name, long salary, long present, long late) {
        return StaffAttendanceSummary.builder().staffId(id.toString()).staffName(name).branchName("Koramangala")
                .proRatedSalary(BigDecimal.valueOf(salary)).daysPresent(present).lateArrivals(late).build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> staff(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("staff");
    }

    private Map<String, Object> run(List<StaffSalesRow> s, List<StaffAttendanceSummary> a,
                                    Map<UUID, Long> customers, Map<UUID, List<StaffScorecardService.ServiceCount>> services) {
        return StaffScorecardService.assemble(s, a, customers, services, 4.0, START, END, 30);
    }

    @Test
    void theMultipleTheGapAndTheVerdictAreWorkedOutInCode() {
        Map<String, Object> result = run(
                List.of(sales(ASHA, "Asha", 50, 100_000), sales(BILAL, "Bilal", 40, 50_000)),
                List.of(att(ASHA, "Asha", 20_000, 26, 2), att(BILAL, "Bilal", 20_000, 24, 5)),
                Map.of(ASHA, 38L, BILAL, 30L), Map.of());

        Map<String, Object> asha = staff(result).get(0);
        assertThat(asha.get("name")).isEqualTo("Asha");
        assertThat(asha.get("targetRevenue")).isEqualTo(80_000L);
        assertThat(asha.get("multipleOfSalary")).isEqualTo(5.0);
        assertThat(asha.get("gapToTarget")).isEqualTo(-20_000L);   // ahead of target
        assertThat(asha.get("meetsTarget")).isEqualTo(true);

        Map<String, Object> bilal = staff(result).get(1);
        assertThat(bilal.get("multipleOfSalary")).isEqualTo(2.5);
        assertThat(bilal.get("gapToTarget")).isEqualTo(30_000L);   // still short
        assertThat(bilal.get("meetsTarget")).isEqualTo(false);
        assertThat(bilal.get("uniqueCustomers")).isEqualTo(30L);
        assertThat(bilal.get("lateArrivals")).isEqualTo(5L);
        assertThat(result.get("totalRevenue")).isEqualTo(150_000L);
    }

    @Test
    void staffAreSortedByRevenueHighestFirst() {
        Map<String, Object> result = run(
                List.of(sales(BILAL, "Bilal", 10, 10_000), sales(ASHA, "Asha", 20, 30_000)),
                List.of(att(ASHA, "Asha", 10_000, 20, 0), att(BILAL, "Bilal", 10_000, 20, 0)), Map.of(), Map.of());

        assertThat(staff(result)).extracting(r -> r.get("name")).containsExactly("Asha", "Bilal");
    }

    @Test
    void someoneWithNoSalaryOnFileGetsNoTargetFiguresRatherThanADivisionByZero() {
        Map<String, Object> result = run(List.of(sales(CHITRA, "Chitra", 5, 12_000)), List.of(att(CHITRA, "Chitra", 0, 10, 0)),
                Map.of(), Map.of());

        Map<String, Object> row = staff(result).get(0);
        assertThat(row.get("multipleOfSalary")).isNull();
        assertThat(row.get("meetsTarget")).isNull();
        assertThat(row.get("targetRevenue")).isNull();
        assertThat(row.get("revenue")).isEqualTo(12_000L);
    }

    @Test
    void aPersonWhoWorkedButBilledNothingStillAppearsWithZeroRevenue() {
        Map<String, Object> result = run(List.of(sales(ASHA, "Asha", 5, 20_000)),
                List.of(att(ASHA, "Asha", 10_000, 20, 0), att(CHITRA, "Chitra", 10_000, 18, 1)), Map.of(), Map.of());

        assertThat(staff(result)).hasSize(2);
        Map<String, Object> chitra = staff(result).get(1);
        assertThat(chitra.get("name")).isEqualTo("Chitra");
        assertThat(chitra.get("revenue")).isEqualTo(0L);
        assertThat(chitra.get("multipleOfSalary")).isEqualTo(0.0);
        assertThat(chitra.get("meetsTarget")).isEqualTo(false);
    }

    @Test
    void servicesAreListedMostPerformedFirstAndCappedAtFive() {
        List<StaffScorecardService.ServiceCount> svc = List.of(
                new StaffScorecardService.ServiceCount("Haircut", 40), new StaffScorecardService.ServiceCount("Colour", 12),
                new StaffScorecardService.ServiceCount("Facial", 9), new StaffScorecardService.ServiceCount("Spa", 7),
                new StaffScorecardService.ServiceCount("Wax", 5), new StaffScorecardService.ServiceCount("Threading", 2));

        Map<String, Object> result = run(List.of(sales(ASHA, "Asha", 75, 90_000)), List.of(att(ASHA, "Asha", 20_000, 26, 0)),
                Map.of(ASHA, 38L), Map.of(ASHA, svc));

        Map<String, Object> row = staff(result).get(0);
        assertThat(row.get("distinctServices")).isEqualTo(6);
        assertThat((List<?>) row.get("topServices")).hasSize(5).first().isEqualTo("Haircut x40");
    }

    @Test
    void theResultExplainsHowToReadItAndNamesThePeriodAndTarget() {
        Map<String, Object> result = run(List.of(), List.of(), Map.of(), Map.of());
        assertThat(result.get("period")).isEqualTo("2026-10-25 to 2026-11-23 (30 days)");
        assertThat(result.get("targetMultiple")).isEqualTo(4.0);
        assertThat((String) result.get("howToRead")).contains("gapToTarget").contains("uniqueCustomers");
    }
}
