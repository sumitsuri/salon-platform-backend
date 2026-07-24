package com.salonplatform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures verified-attendance columns exist and PostgreSQL check constraints allow VERIFIED method.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class AttendanceSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS entry_latitude DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS entry_longitude DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS entry_accuracy_meters DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS exit_latitude DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS exit_longitude DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS exit_accuracy_meters DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS entry_geo_status VARCHAR(32)");
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS exit_geo_status VARCHAR(32)");
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS entry_photo_key VARCHAR(512)");
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS exit_photo_key VARCHAR(512)");
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS entry_verified BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS exit_verified BOOLEAN DEFAULT FALSE");
            jdbcTemplate.execute("UPDATE attendance_records SET entry_verified = FALSE WHERE entry_verified IS NULL");
            jdbcTemplate.execute("UPDATE attendance_records SET exit_verified = FALSE WHERE exit_verified IS NULL");

            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS geofence_radius_meters INTEGER");
            jdbcTemplate.execute("ALTER TABLE branches ADD COLUMN IF NOT EXISTS attendance_grace_minutes INTEGER");

            relaxMethodCheck("attendance_records_entry_method_check", "entry_method");
            relaxMethodCheck("attendance_records_exit_method_check", "exit_method");

            log.info("Attendance schema patch applied");
        } catch (Exception e) {
            log.warn("Attendance schema patch skipped or partial: {}", e.getMessage());
        }
    }

    private void relaxMethodCheck(String constraintName, String column) {
        jdbcTemplate.execute("ALTER TABLE attendance_records DROP CONSTRAINT IF EXISTS " + constraintName);
        jdbcTemplate.execute(
                "ALTER TABLE attendance_records ADD CONSTRAINT " + constraintName
                        + " CHECK (" + column + " IS NULL OR " + column + " IN ('BIOMETRIC', 'MANUAL', 'VERIFIED'))");
    }
}
