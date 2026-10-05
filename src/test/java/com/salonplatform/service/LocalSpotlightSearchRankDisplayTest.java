package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.enums.BranchBusinessType;
import com.salonplatform.dto.analytics.LocalSpotlightResponse;
import com.salonplatform.google.DigitalPresenceSyncService;
import com.salonplatform.google.GooglePlacesProperties;
import com.salonplatform.google.GoogleSearchRankEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalSpotlightSearchRankDisplayTest {

    @Mock
    private DigitalPresenceSyncService digitalPresenceSyncService;
    @Mock
    private LocalSpotlightDailyRankService localSpotlightDailyRankService;

    private LocalSpotlightService service;

    @BeforeEach
    void setUp() {
        service = new LocalSpotlightService(
                null,
                null,
                null,
                digitalPresenceSyncService,
                new GooglePlacesProperties(),
                null,
                localSpotlightDailyRankService);
    }

    @Test
    void buildSearchRanks_doesNotReuseEstimatedRankWhenPinKeywordsStale() throws Exception {
        Branch branch = branchWithPin();
        branch.setEstimatedSearchRank(3);
        branch.setGoogleSearchRankData(null);

        when(digitalPresenceSyncService.readRankEntries(branch)).thenReturn(List.of(
                GoogleSearchRankEntry.builder()
                        .keyword("salon near Varthur")
                        .yourRank(3)
                        .topThreePlaces(List.of())
                        .build()));
        when(localSpotlightDailyRankService.today()).thenReturn(java.time.LocalDate.of(2026, 10, 5));
        when(localSpotlightDailyRankService.findSnapshot(any(), any(), any())).thenReturn(java.util.Optional.empty());

        Object result = invokeBuildSearchRanks(branch, null);
        @SuppressWarnings("unchecked")
        List<LocalSpotlightResponse.SearchRankRow> rows =
                (List<LocalSpotlightResponse.SearchRankRow>) result.getClass()
                        .getDeclaredMethod("rows")
                        .invoke(result);
        int storedCount = (Integer) result.getClass().getDeclaredMethod("storedCount").invoke(result);
        assertEquals(0, storedCount);

        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().noneMatch(r -> Integer.valueOf(3).equals(r.getYourRank())),
                "must not copy branch estimatedSearchRank onto every keyword");
        assertTrue(rows.stream().allMatch(r -> "Refresh from Google".equals(r.getYourRankLabel())));
    }

    private static Branch branchWithPin() {
        Branch branch = new Branch();
        branch.setId(java.util.UUID.randomUUID());
        branch.setName("Mystic Varthur");
        branch.setCode("MW01");
        branch.setAddress("SLV Sunrise, Varthur, Bangalore 560087");
        branch.setBusinessType(BranchBusinessType.SALON_AND_SPA);
        return branch;
    }

    private Object invokeBuildSearchRanks(Branch branch, java.time.LocalDate compareDate) throws Exception {
        Method m = LocalSpotlightService.class.getDeclaredMethod(
                "buildSearchRanks", Branch.class, java.time.LocalDate.class);
        m.setAccessible(true);
        return m.invoke(service, branch, compareDate);
    }
}
