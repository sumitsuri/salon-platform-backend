package com.salonplatform.service;

import com.salonplatform.dto.platform.PlatformUsageInsights.Health;
import com.salonplatform.dto.platform.PlatformUsageInsights.Totals;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlatformUsageInsightsServiceTest {

    private static final LocalDate END = LocalDate.of(2026, 10, 12);
    private static final PlatformUsageInsightsService.Range RANGE =
            PlatformUsageInsightsService.Range.ofDates(END.minusDays(27), END);

    private static Totals.TotalsBuilder base() {
        return Totals.builder()
                .events(300)
                .previousEvents(280)
                .activeUsers(8)
                .previousActiveUsers(8)
                .wau(8)
                .mau(8)
                .provisionedUsers(8)
                .distinctFeatures(8)
                .lastActivityAt(END.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC));
    }

    @Test
    void busyBroadAccountIsHealthy() {
        Health h = PlatformUsageInsightsService.health(base().build(), 20, RANGE);
        assertEquals("HEALTHY", h.getStatus());
        assertTrue(h.getScore() >= 70);
        assertEquals(100, h.getAdoption());
        assertEquals(100, h.getBreadth());
    }

    @Test
    void silentAccountIsDormantWithLowScore() {
        Totals t = base().events(0).activeUsers(0).wau(0).mau(0).distinctFeatures(0).lastActivityAt(null).build();
        Health h = PlatformUsageInsightsService.health(t, 20, RANGE);
        assertEquals("DORMANT", h.getStatus());
        assertTrue(h.getScore() <= 25);
    }

    @Test
    void activityOlderThanTwoWeeksIsDormantEvenIfRangeHadEvents() {
        Instant old = END.minusDays(20).atTime(9, 0).toInstant(java.time.ZoneOffset.UTC);
        Health h = PlatformUsageInsightsService.health(base().lastActivityAt(old).wau(0).build(), 20, RANGE);
        assertEquals("DORMANT", h.getStatus());
        assertTrue(h.getScore() <= 25, "past activity must not make a silent account look fine");
    }

    @Test
    void steepFallInABusyAccountIsAtRiskAndScoreAgrees() {
        Totals t = base().events(50).previousEvents(200).build();
        Health h = PlatformUsageInsightsService.health(t, 20, RANGE);
        assertEquals("AT_RISK", h.getStatus());
        assertTrue(h.getScore() <= 44);
    }

    @Test
    void firstPeriodWithActivityIsNew() {
        Totals t = base().previousEvents(0).previousActiveUsers(0).build();
        assertEquals("NEW", PlatformUsageInsightsService.health(t, 20, RANGE).getStatus());
    }

    @Test
    void lowAdoptionAndShallowFeatureUseLandsInWatchOrRisk() {
        Totals t = base().activeUsers(2).wau(1).mau(2).distinctFeatures(2).events(120).previousEvents(120).build();
        Health h = PlatformUsageInsightsService.health(t, 20, RANGE);
        assertTrue(h.getScore() < 70, "got " + h.getScore());
        assertNotEquals("HEALTHY", h.getStatus());
    }

    @Test
    void activeUsersAboveProvisionedDoNotInflateAdoptionPastFull() {
        Health h = PlatformUsageInsightsService.health(base().activeUsers(12).provisionedUsers(8).build(), 20, RANGE);
        assertEquals(100, h.getAdoption());
    }

    @Test
    void breadthIsJudgedAgainstWhatExistsWhenFewFeaturesAreTracked() {
        Health h = PlatformUsageInsightsService.health(base().distinctFeatures(3).build(), 3, RANGE);
        assertEquals(100, h.getBreadth());
    }

    @Test
    void bucketizeZeroFillsAndPreservesTotals() {
        var range = PlatformUsageInsightsService.Range.ofDates(END.minusDays(9), END);
        List<Integer> out = PlatformUsageInsightsService.bucketize(
                Map.of(END, 5, END.minusDays(9), 2, END.minusDays(3), 1), range);
        assertEquals(10, out.size());
        assertEquals(2, out.get(0));
        assertEquals(5, out.get(9));
        assertEquals(8, out.stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void bucketizeCapsLongRangesAndKeepsEveryEvent() {
        var range = PlatformUsageInsightsService.Range.ofDates(END.minusDays(364), END);
        Map<LocalDate, Integer> perDay = new java.util.HashMap<>();
        for (int i = 0; i < 365; i++) {
            perDay.put(END.minusDays(i), 1);
        }
        List<Integer> out = PlatformUsageInsightsService.bucketize(perDay, range);
        assertTrue(out.size() <= 60);
        assertEquals(365, out.stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void previousRangeIsTheSameLengthImmediatelyBefore() {
        var prev = RANGE.previous();
        assertEquals(RANGE.days(), prev.days());
        assertEquals(RANGE.fromDate().minusDays(1), prev.toDate());
    }

    @Test
    void bucketizeHandlesMissingSeries() {
        assertEquals(RANGE.days(), PlatformUsageInsightsService.bucketize(null, RANGE).stream().mapToInt(x -> 1).sum());
    }
}
