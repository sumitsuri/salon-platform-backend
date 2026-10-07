package com.salonplatform.google;

/** Google Text Search depth for Local Spotlight keyword rank sync. */
public final class LocalSpotlightSearchConfig {

    /** Pages of 20 results each (Places API New pagination). */
    public static final int TEXT_SEARCH_MAX_PAGES = 2;

    public static int maxRankResults() {
        return TEXT_SEARCH_MAX_PAGES * 20;
    }

    private LocalSpotlightSearchConfig() {}
}
