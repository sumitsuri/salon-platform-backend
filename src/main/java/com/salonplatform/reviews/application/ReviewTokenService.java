package com.salonplatform.reviews.application;

import com.salonplatform.reviews.config.ReviewsProperties;
import com.salonplatform.exception.BadRequestException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
public class ReviewTokenService {

    private final byte[] secret;
    private final int expiryDays;

    public ReviewTokenService(ReviewsProperties properties) {
        this.secret = properties.getTokenSecret().getBytes(StandardCharsets.UTF_8);
        this.expiryDays = properties.getTokenExpiryDays();
    }

    public String createToken(UUID invitationId) {
        long exp = Instant.now().plus(expiryDays, ChronoUnit.DAYS).toEpochMilli();
        String payload = invitationId + ":" + exp;
        String signature = sign(payload);
        String raw = payload + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public UUID validateAndGetInvitationId(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 3) {
                throw new BadRequestException("Invalid review link");
            }
            UUID invitationId = UUID.fromString(parts[0]);
            long exp = Long.parseLong(parts[1]);
            String signature = parts[2];

            if (Instant.now().toEpochMilli() > exp) {
                throw new BadRequestException("Review link expired");
            }

            String expected = sign(parts[0] + ":" + parts[1]);
            if (!expected.equals(signature)) {
                throw new BadRequestException("Invalid review link");
            }
            return invitationId;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Invalid review link");
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign review token", ex);
        }
    }
}
