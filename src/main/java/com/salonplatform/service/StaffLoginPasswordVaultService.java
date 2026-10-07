package com.salonplatform.service;

import com.salonplatform.domain.entity.User;
import com.salonplatform.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Stores staff login passwords encrypted at rest so brand admins can hand credentials to employees.
 * Login still uses bcrypt on {@link User#getPassword()}; this vault is admin-recoverable only.
 */
@Service
public class StaffLoginPasswordVaultService {

    private static final String PREFIX = "v1:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKey aesKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public StaffLoginPasswordVaultService(@Value("${app.jwt.secret}") String jwtSecret) {
        this.aesKey = deriveKey(jwtSecret);
    }

    public void storePlaintext(User user, String plainPassword) {
        if (user == null || plainPassword == null || plainPassword.isBlank()) {
            return;
        }
        user.setLoginPasswordVault(encrypt(plainPassword));
    }

    public Optional<String> revealPlaintext(User user) {
        if (user == null || user.getLoginPasswordVault() == null || user.getLoginPasswordVault().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(decrypt(user.getLoginPasswordVault()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String encrypt(String plain) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new BadRequestException("Could not secure staff login password");
        }
    }

    private String decrypt(String stored) {
        if (!stored.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unknown vault format");
        }
        byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);
        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("Vault decrypt failed", e);
        }
    }

    private static SecretKey deriveKey(String secret) {
        try {
            byte[] raw = secret != null ? secret.getBytes(StandardCharsets.UTF_8) : new byte[0];
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(raw);
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive vault key", e);
        }
    }
}
