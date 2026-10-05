package com.salonplatform.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.LocalSpotlightSerpCache;
import com.salonplatform.domain.repository.LocalSpotlightSerpCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared Google text-search SERP snapshots — one fetch per pin-code + keyword per day
 * (or per branch for "near me" queries), reused across brands in the same PIN.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalSpotlightSerpCacheService {

    public static final ZoneId SNAPSHOT_ZONE = ZoneId.of("Asia/Kolkata");

    private final GooglePlacesClient googlePlacesClient;
    private final LocalSpotlightSerpCacheRepository serpCacheRepository;
    private final ObjectMapper objectMapper;

    public record CachedSerp(List<GoogleRankedPlace> rankedPlaces) {}

    @Transactional
    public CachedSerp resolveSerp(Branch branch, String keyword, int radiusMeters, LocalDate snapshotDate,
                                  boolean forceRefresh) {
        String cacheKey = LocalSpotlightKeywords.serpCacheKey(branch, keyword);
        if (!forceRefresh) {
            var hit = serpCacheRepository.findByCacheKeyAndSnapshotDate(cacheKey, snapshotDate);
            if (hit.isPresent()) {
                return deserialize(hit.get().getSerpJson());
            }
        }

        double lat = branch.getLatitude();
        double lng = branch.getLongitude();
        List<GooglePlaceSnapshot> results =
                googlePlacesClient.searchText(keyword, lat, lng, radiusMeters, 20);
        List<GoogleRankedPlace> ranked = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            GooglePlaceSnapshot snap = results.get(i);
            ranked.add(GoogleRankedPlace.builder()
                    .rank(i + 1)
                    .name(snap.getName())
                    .googlePlaceId(snap.getPlaceId())
                    .googleMapsUrl(snap.mapsUriOrFallback())
                    .build());
        }

        String json = writeJson(ranked);
        LocalSpotlightSerpCache row = serpCacheRepository.findByCacheKeyAndSnapshotDate(cacheKey, snapshotDate)
                .orElseGet(() -> LocalSpotlightSerpCache.builder()
                        .cacheKey(cacheKey)
                        .snapshotDate(snapshotDate)
                        .build());
        row.setSerpJson(json);
        row.setFetchedAt(Instant.now());
        serpCacheRepository.save(row);
        log.debug("Cached SERP {} for {} ({} places)", cacheKey, snapshotDate, ranked.size());
        return new CachedSerp(ranked);
    }

    public GooglePlacesClient.TextSearchInsight insightForBranch(CachedSerp serp, String ownPlaceId) {
        String target = ownPlaceId != null && !ownPlaceId.isBlank()
                ? GooglePlacesClient.normalizePlaceId(ownPlaceId)
                : "";
        int rank = -1;
        List<GoogleRankedPlace> topThree = new ArrayList<>();
        for (GoogleRankedPlace place : serp.rankedPlaces()) {
            if (place.getRank() <= 3) {
                topThree.add(place);
            }
            if (!target.isBlank()
                    && target.equals(GooglePlacesClient.normalizePlaceId(place.getGooglePlaceId()))) {
                rank = place.getRank();
            }
        }
        return new GooglePlacesClient.TextSearchInsight(rank, topThree);
    }

    private CachedSerp deserialize(String json) {
        try {
            List<GoogleRankedPlace> places =
                    objectMapper.readValue(json, new TypeReference<>() {});
            return new CachedSerp(places != null ? places : List.of());
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse SERP cache JSON: {}", e.getMessage());
            return new CachedSerp(List.of());
        }
    }

    private String writeJson(List<GoogleRankedPlace> ranked) {
        try {
            return objectMapper.writeValueAsString(ranked);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SERP cache", e);
        }
    }
}
