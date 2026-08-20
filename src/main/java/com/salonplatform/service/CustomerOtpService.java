package com.salonplatform.service;

import com.salonplatform.dto.publicbook.PublicBookModels;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerOtpService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public PublicBookModels.OtpResponse sendOtp(UUID tenantId, String rawPhone) {
        String phone = normalizePhone(rawPhone);
        if (phone == null || phone.length() < 10) {
            throw new BadRequestException("Enter a valid mobile number");
        }

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expires = Instant.now().plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES);
        UUID id = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO customer_otp_challenges (id, tenant_id, phone, otp_hash, expires_at) VALUES (?, ?, ?, ?, ?)",
                id, tenantId, phone, passwordEncoder.encode(otp), java.sql.Timestamp.from(expires));

        log.info("Booking OTP for tenant {} phone {}****: {}", tenantId, phone.substring(0, Math.min(4, phone.length())), otp);

        return PublicBookModels.OtpResponse.builder()
                .sent(true)
                .message("Verification code sent")
                .devOtp(otp)
                .build();
    }

    @Transactional
    public void verifyOtp(UUID tenantId, String rawPhone, String otp) {
        String phone = normalizePhone(rawPhone);
        if (phone == null || otp == null || otp.isBlank()) {
            throw new BadRequestException("Phone and verification code are required");
        }

        var rows = jdbcTemplate.queryForList("""
                SELECT id, otp_hash, expires_at, verified_at
                FROM customer_otp_challenges
                WHERE tenant_id = ? AND phone = ?
                ORDER BY created_at DESC
                LIMIT 5
                """, tenantId, phone);

        Instant now = Instant.now();
        for (var row : rows) {
            Instant expires = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
            if (expires.isBefore(now)) {
                continue;
            }
            if (row.get("verified_at") != null) {
                continue;
            }
            String hash = (String) row.get("otp_hash");
            if (passwordEncoder.matches(otp.trim(), hash)) {
                UUID id = (UUID) row.get("id");
                jdbcTemplate.update(
                        "UPDATE customer_otp_challenges SET verified_at = ? WHERE id = ?",
                        java.sql.Timestamp.from(now), id);
                return;
            }
        }
        throw new BadRequestException("Invalid or expired verification code");
    }

    private static String normalizePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() >= 10) {
            return digits.substring(digits.length() - 10);
        }
        return digits.isEmpty() ? null : digits;
    }
}
