package com.salonplatform.service;

import com.salonplatform.config.OfferPosterProperties;
import com.salonplatform.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Stores generated poster images in S3 (production) or a local folder (development). */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfferPosterStorageService {

    private final OfferPosterProperties properties;
    private S3Client s3Client;

    @PostConstruct
    void init() {
        if (isS3Enabled()) {
            s3Client = S3Client.builder().region(Region.of(properties.getAwsRegion())).build();
            log.info("Offer poster storage: S3 bucket={} prefix={}", properties.getS3Bucket(), properties.getKeyPrefix());
        } else {
            try {
                Files.createDirectories(root());
            } catch (IOException e) {
                log.warn("Could not create offer poster dir {}: {}", root(), e.getMessage());
            }
            log.info("Offer poster storage: local dir={}", root());
        }
    }

    public boolean isS3Enabled() {
        return properties.getS3Bucket() != null && !properties.getS3Bucket().isBlank();
    }

    /** Stores the image and returns the key to keep on the poster row. */
    public String store(UUID tenantId, UUID offerId, UUID posterId, byte[] bytes, String contentType) {
        String ext = contentType != null && contentType.contains("png") ? "png" : "jpg";
        String relativeKey = tenantId + "/" + offerId + "/" + posterId + "." + ext;
        try {
            if (isS3Enabled()) {
                String key = properties.getKeyPrefix() + relativeKey;
                s3Client.putObject(
                        PutObjectRequest.builder().bucket(properties.getS3Bucket()).key(key).contentType(contentType).build(),
                        RequestBody.fromBytes(bytes));
                return key;
            }
            Path target = root().resolve(relativeKey);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return relativeKey;
        } catch (IOException | RuntimeException e) {
            log.error("Failed to store offer poster {} (s3={}): {}", posterId, isS3Enabled(), e.toString());
            throw new BadRequestException("Failed to store the poster image");
        }
    }

    public byte[] load(String key) {
        if (key == null || key.contains("..")) {
            throw new BadRequestException("Invalid poster key");
        }
        if (isS3Key(key)) {
            try {
                return s3Client.getObjectAsBytes(
                        GetObjectRequest.builder().bucket(properties.getS3Bucket()).key(key).build()).asByteArray();
            } catch (Exception e) {
                log.warn("Failed to load offer poster from S3 key={}: {}", key, e.getMessage());
                throw new BadRequestException("Poster image not found");
            }
        }
        Path path = root().resolve(key);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BadRequestException("Poster image not found");
        }
    }

    /** Best effort; a leftover file is harmless. */
    public void delete(String key) {
        if (key == null || key.contains("..")) return;
        try {
            if (isS3Key(key)) {
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.getS3Bucket()).key(key).build());
            } else {
                Files.deleteIfExists(root().resolve(key));
            }
        } catch (Exception e) {
            log.debug("Could not delete poster {}: {}", key, e.getMessage());
        }
    }

    private boolean isS3Key(String key) {
        String prefix = properties.getKeyPrefix();
        return isS3Enabled() && prefix != null && !prefix.isBlank() && key.startsWith(prefix);
    }

    private Path root() {
        return Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
    }
}
