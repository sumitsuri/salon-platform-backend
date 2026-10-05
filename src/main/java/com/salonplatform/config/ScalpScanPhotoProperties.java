package com.salonplatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.scalp-scan.photos")
public class ScalpScanPhotoProperties {
    private String s3Bucket = "";
    /** Under {@code attendance/} so prod EC2 IAM (attendance/*) can write scan photos. */
    private String keyPrefix = "attendance/scalp-scans/";
    private String storageDir = "data/scalp-scan-photos";
    private String awsRegion = "ap-south-1";
    private long maxBytes = 2097152;
}
