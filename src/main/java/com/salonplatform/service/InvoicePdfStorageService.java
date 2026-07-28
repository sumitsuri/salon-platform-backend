package com.salonplatform.service;

import com.salonplatform.config.InvoicePdfProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Persists invoice PDFs to S3 when configured, otherwise to local disk.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfStorageService {

    private final InvoicePdfProperties properties;
    private S3Client s3Client;

    @PostConstruct
    void init() {
        if (isS3Enabled()) {
            s3Client = S3Client.builder()
                    .region(Region.of(properties.getAwsRegion()))
                    .build();
            log.info("Invoice PDF storage: S3 bucket={}", properties.getS3Bucket());
        } else {
            try {
                Files.createDirectories(Path.of(properties.getLocalDir()));
            } catch (IOException e) {
                log.warn("Could not create local invoice PDF dir: {}", e.getMessage());
            }
            log.info("Invoice PDF storage: local dir={}", properties.getLocalDir());
        }
    }

    public boolean isS3Enabled() {
        return properties.getS3Bucket() != null && !properties.getS3Bucket().isBlank();
    }

    public String store(UUID tenantId, UUID invoiceId, String invoiceNumber, byte[] pdf) {
        String safeNumber = invoiceNumber == null ? invoiceId.toString() : invoiceNumber.replaceAll("[^A-Za-z0-9._-]", "_");
        String relativeKey = properties.getKeyPrefix()
                + tenantId + "/"
                + safeNumber + "-" + invoiceId + ".pdf";

        if (isS3Enabled()) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getS3Bucket())
                            .key(relativeKey)
                            .contentType("application/pdf")
                            .build(),
                    RequestBody.fromBytes(pdf));
            return relativeKey;
        }

        Path path = Path.of(properties.getLocalDir(), tenantId.toString(), safeNumber + "-" + invoiceId + ".pdf");
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, pdf);
            return path.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store invoice PDF locally", e);
        }
    }

    public byte[] load(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        if (isS3Enabled() && !storageKey.startsWith("/") && !storageKey.contains(":\\")) {
            try {
                return s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                                .bucket(properties.getS3Bucket())
                                .key(storageKey)
                                .build())
                        .asByteArray();
            } catch (Exception e) {
                log.warn("Failed to load invoice PDF from S3 key={}: {}", storageKey, e.getMessage());
                return null;
            }
        }
        try {
            Path path = Path.of(storageKey);
            if (Files.exists(path)) {
                return Files.readAllBytes(path);
            }
        } catch (IOException e) {
            log.warn("Failed to load local invoice PDF {}: {}", storageKey, e.getMessage());
        }
        return null;
    }
}
