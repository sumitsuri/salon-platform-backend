package com.salonplatform.service;

import com.salonplatform.config.PasswordResetProperties;
import com.salonplatform.domain.entity.PasswordResetToken;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.repository.PasswordResetTokenRepository;
import com.salonplatform.domain.repository.RefreshTokenRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.notification.SesEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SesEmailService sesEmailService;
    private final PasswordResetProperties properties;

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email.trim())
                .filter(User::isActive)
                .ifPresent(this::issueResetToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNull(hashToken(rawToken))
                .orElseThrow(() -> new BadRequestException("error.passwordReset.invalid"));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("error.passwordReset.expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BadRequestException("error.passwordReset.invalid"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    private void issueResetToken(User user) {
        passwordResetTokenRepository.deleteByUserId(user.getId());

        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(properties.getTokenExpiryMinutes(), ChronoUnit.MINUTES);

        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(hashToken(rawToken))
                .expiresAt(expiresAt)
                .build());

        String resetUrl = buildResetUrl(rawToken);
        SesEmailService.EmailSendResult result =
                sesEmailService.sendPasswordResetEmail(user.getEmail(), user.getName(), resetUrl);

        if ("FAILED".equals(result.status())) {
            log.warn("Password reset email failed for user {}: {}", user.getId(), result.detail());
        } else if ("SKIPPED".equals(result.status())) {
            log.warn("Password reset link for {} (SES disabled): {}", user.getEmail(), resetUrl);
        }
    }

    private String buildResetUrl(String rawToken) {
        String base = properties.getFrontendBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/reset-password?token=" + rawToken;
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
