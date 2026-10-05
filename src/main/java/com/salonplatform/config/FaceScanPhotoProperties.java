package com.salonplatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.face-scan.photos")
public class FaceScanPhotoProperties {
    private String s3Bucket = "";
    /** Under attendance/* for prod EC2 IAM. */
    private String keyPrefix = "attendance/face-scans/";
    private String storageDir = "data/face-scan-photos";
    private String awsRegion = "ap-south-1";
    private long maxBytes = 2097152;
}
