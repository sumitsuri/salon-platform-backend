package com.salonplatform.service;

import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffSuggestedPasswordService {

    private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int MAX_ATTEMPTS = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public String generateUniqueForTenant(UUID tenantId) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID scopedTenant = SecurityUtils.requireTenantId();
        if (!scopedTenant.equals(tenantId)) {
            throw new BadRequestException("Invalid tenant");
        }
        List<User> users = userRepository.findByTenantId(tenantId);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomPassword();
            if (!matchesAnyUser(candidate, users)) {
                return candidate;
            }
        }
        throw new BadRequestException("Could not generate a unique password — try again");
    }

    public void assertPasswordNotReused(UUID tenantId, String rawPassword, UUID excludeUserId) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return;
        }
        List<User> users = userRepository.findByTenantId(tenantId);
        for (User user : users) {
            if (excludeUserId != null && excludeUserId.equals(user.getId())) {
                continue;
            }
            if (user.getPassword() != null && passwordEncoder.matches(rawPassword, user.getPassword())) {
                throw new BadRequestException(
                        "This password is already used by another account — generate a new one");
            }
        }
    }

    private boolean matchesAnyUser(String raw, List<User> users) {
        for (User user : users) {
            if (user.getPassword() != null && passwordEncoder.matches(raw, user.getPassword())) {
                return true;
            }
        }
        return false;
    }

    private String randomPassword() {
        int length = 6 + random.nextInt(3);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }
}
