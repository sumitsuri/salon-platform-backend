package com.salonplatform.service;

import com.salonplatform.config.AttendancePhotoProperties;
import com.salonplatform.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendancePhotoStorageService {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    private final AttendancePhotoProperties properties;

    public String store(UUID tenantId, UUID branchId, UUID staffId, String punchType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Photo is required for verified punch");
        }
        if (file.getSize() > properties.getMaxBytes()) {
            throw new BadRequestException("Photo must be under 1 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank() || "application/octet-stream".equalsIgnoreCase(contentType)) {
            String name = file.getOriginalFilename();
            if (name != null && name.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (name != null && name.toLowerCase().endsWith(".webp")) {
                contentType = "image/webp";
            } else {
                contentType = "image/jpeg";
            }
        }
        if (!ALLOWED.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Photo must be JPEG, PNG, or WebP");
        }

        String ext = contentType.contains("png") ? "png" : contentType.contains("webp") ? "webp" : "jpg";
        String key = tenantId + "/" + branchId + "/" + staffId + "/" + punchType + "-" + UUID.randomUUID() + "." + ext;
        Path dir = Paths.get(properties.getStorageDir());
        Path target = dir.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new BadRequestException("Failed to store attendance photo");
        }
        return key;
    }

    public byte[] load(String key) {
        if (key == null || key.contains("..")) {
            throw new BadRequestException("Invalid photo key");
        }
        Path path = Paths.get(properties.getStorageDir()).resolve(key);
        if (!Files.exists(path)) {
            throw new BadRequestException("Photo not found");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read photo");
        }
    }

    public String contentTypeForKey(String key) {
        if (key == null) return "image/jpeg";
        if (key.endsWith(".png")) return "image/png";
        if (key.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    public void delete(String key) {
        if (key == null || key.contains("..")) return;
        try {
            Files.deleteIfExists(Paths.get(properties.getStorageDir()).resolve(key));
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
