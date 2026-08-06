package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Remove seeded demo Google URLs so Local Spotlight uses live Google Places data only. */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DemoGoogleDataCleanupPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int branches = jdbcTemplate.update(
                    "UPDATE branches SET google_maps_url = NULL, google_review_url = NULL, "
                            + "google_place_id = NULL, google_rating = NULL, google_review_count = NULL, "
                            + "gbp_photo_count = NULL, gbp_video_count = NULL, estimated_search_rank = NULL, "
                            + "digital_presence_updated_at = NULL, google_formatted_address = NULL "
                            + "WHERE google_maps_url LIKE '%cid=demo-%' OR google_review_url LIKE '%demo-%'");
            int rivals = jdbcTemplate.update(
                    "UPDATE local_competitors SET google_rating = NULL, google_review_count = NULL, "
                            + "gbp_photo_count = NULL, gbp_video_count = NULL, estimated_search_rank = NULL, "
                            + "google_place_id = NULL, google_maps_url = NULL "
                            + "WHERE google_maps_url IS NULL AND google_rating IS NOT NULL AND google_place_id IS NULL");
            if (branches > 0 || rivals > 0) {
                log.info("Cleared demo Google placeholder data (branches={}, rivals={})", branches, rivals);
            }
        } catch (Exception e) {
            log.warn("Demo Google data cleanup skipped: {}", e.getMessage());
        }
    }
}
