package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DigitalPresenceSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS google_place_id VARCHAR(128)");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS google_maps_url VARCHAR(512)");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS google_rating DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS google_review_count INTEGER");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS gbp_photo_count INTEGER");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS gbp_video_count INTEGER");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS gbp_has_phone BOOLEAN");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS gbp_has_website BOOLEAN");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS gbp_has_hours BOOLEAN");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS gbp_has_book_button BOOLEAN");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS gbp_services_listed_count INTEGER");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS estimated_search_rank INTEGER");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS digital_presence_updated_at TIMESTAMPTZ");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS google_formatted_address VARCHAR(512)");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS google_search_rank_data TEXT");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS google_low_rating_review_count INTEGER");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS google_reviews_sample_size INTEGER");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS google_review_auto_publish BOOLEAN DEFAULT TRUE");

            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS google_rating DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS google_place_id VARCHAR(128)");
            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS google_maps_url VARCHAR(512)");
            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS google_auto_discovered BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS google_synced_at TIMESTAMPTZ");
            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS google_low_rating_review_count INTEGER");
            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS google_reviews_sample_size INTEGER");
            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS google_review_count INTEGER");
            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS gbp_photo_count INTEGER");
            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS gbp_video_count INTEGER");
            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS gbp_has_phone BOOLEAN");
            jdbcTemplate.execute("ALTER TABLE local_competitors ADD COLUMN IF NOT EXISTS estimated_search_rank INTEGER");

            log.info("Digital presence schema patch applied");
        } catch (Exception e) {
            log.warn("Digital presence schema patch skipped or partial: {}", e.getMessage());
        }
    }
}
