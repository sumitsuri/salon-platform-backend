package com.salonplatform.google;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSpotlightSerpCacheServiceTest {

    @Test
    void insightForBranch_returnsExactRankBeyondFirstPage() {
        List<GoogleRankedPlace> places = List.of(
                place(1, "A", "places/a"),
                place(2, "B", "places/b"),
                place(25, "Mine", "places/mine"));
        var serp = new LocalSpotlightSerpCacheService.CachedSerp(places);

        var insight = LocalSpotlightSerpCacheService.insightForBranch(serp, "places/mine");

        assertThat(insight.rank()).isEqualTo(25);
    }

    private static GoogleRankedPlace place(int rank, String name, String id) {
        return GoogleRankedPlace.builder().rank(rank).name(name).googlePlaceId(id).build();
    }
}
