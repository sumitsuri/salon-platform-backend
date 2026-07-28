package com.salonplatform.service;

import com.salonplatform.config.AttendancePhotoProperties;
import com.salonplatform.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Persists attendance punch selfies to S3 when configured, otherwise to local disk.
 * Uses {@link Files#copy} (not {@code MultipartFile#transferTo}) so Tomcat's Part.write
 * relative-path quirks cannot fail store under a nested destination.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendancePhotoStorageService {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    private final AttendancePhotoProperties properties;
    private S3Client s3Client;

    @PostConstruct
    void init() {
        if (isS3Enabled()) {
            s3Client = S3Client.builder()
                    .region(Region.of(properties.getAwsRegion()))
                    .build();
            log.info("Attendance photo storage: S3 bucket={} prefix={}",
                    properties.getS3Bucket(), properties.getKeyPrefix());
        } else {
            try {
                Files.createDirectories(Path.of(properties.getStorageDir()).toAbsolutePath().normalize());
            } catch (IOException e) {
                log.warn("Could not create local attendance photo dir {}: {}",
                        properties.getStorageDir(), e.getMessage());
            }
            log.info("Attendance photo storage: local dir={}",
                    Path.of(properties.getStorageDir()).toAbsolutePath().normalize());
        }
    }

    public boolean isS3Enabled() {
        return properties.getS3Bucket() != null && !properties.getS3Bucket().isBlank();
    }

    public String store(UUID tenantId, UUID branchId, UUID staffId, String punchType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Photo is required for verified punch");
        }
        if (file.getSize() > properties.getMaxBytes()) {
            throw new BadRequestException("Photo must be under 1 MB");
        }
        String contentType = resolveContentType(file);
        if (!ALLOWED.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Photo must be JPEG, PNG, or WebP");
        }

        String ext = contentType.contains("png") ? "png" : contentType.contains("webp") ? "webp" : "jpg";
        String relativeKey = tenantId + "/" + branchId + "/" + staffId + "/"
                + punchType + "-" + UUID.randomUUID() + "." + ext;

        try {
            if (isS3Enabled()) {
                String s3Key = properties.getKeyPrefix() + relativeKey;
                byte[] bytes = file.getBytes();
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(properties.getS3Bucket())
                                .key(s3Key)
                                .contentType(contentType)
                                .build(),
                        RequestBody.fromBytes(bytes));
                return s3Key;
            }

            Path target = Path.of(properties.getStorageDir()).toAbsolutePath().normalize().resolve(relativeKey);
            Files.createDirectories(target.getParent());
            // Prefer stream copy over transferTo — Tomcat Part.write mishandles nested relative paths.
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return relativeKey;
        } catch (IOException e) {
            log.error("Failed to store attendance photo tenant={} staff={} s3={}: {}",
                    tenantId, staffId, isS3Enabled(), e.toString());
            throw new BadRequestException("Failed to store attendance photo");
        } catch (RuntimeException e) {
            log.error("Failed to store attendance photo tenant={} staff={} s3={}: {}",
                    tenantId, staffId, isS3Enabled(), e.toString());
            throw new BadRequestException("Failed to store attendance photo");
        }
    }

    public byte[] load(String key) {
        if (key == null || key.contains("..")) {
            throw new BadRequestException("Invalid photo key");
        }
        if (isS3Key(key)) {
            try {
                return s3Client.getObjectAsBytes(
                                GetObjectRequest.builder()
                                        .bucket(properties.getS3Bucket())
                                        .key(key)
                                        .build())
                        .asByteArray();
            } catch (Exception e) {
                log.warn("Failed to load attendance photo from S3 key={}: {}", key, e.getMessage());
                throw new BadRequestException("Photo not found");
            }
        }
        Path path = Path.of(properties.getStorageDir()).toAbsolutePath().normalize().resolve(key);
        if (!Files.exists(path)) {
            throw new BadRequestException("Photo not found");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.warn("Failed to read attendance photo {}: {}", key, e.getMessage());
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
        if (isS3Key(key)) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.getS3Bucket())
                        .key(key)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to delete attendance photo from S3 key={}: {}", key, e.getMessage());
            }
            return;
        }
        try {
            Files.deleteIfExists(Path.of(properties.getStorageDir()).toAbsolutePath().normalize().resolve(key));
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private boolean isS3Key(String key) {
        if (!isS3Enabled()) {
            return false;
        }
        String prefix = properties.getKeyPrefix();
        return prefix != null && !prefix.isBlank() && key.startsWith(prefix);
    }

    private static String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank() || "application/octet-stream".equalsIgnoreCase(contentType)) {
            String name = file.getOriginalFilename();
            if (name != null && name.toLowerCase().endsWith(".png")) {
                return "image/png";
            }
            if (name != null && name.toLowerCase().endsWith(".webp")) {
                return "image/webp";
            }
            return "image/jpeg";
        }
        return contentType;
    }
}
