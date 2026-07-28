package com.salonplatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.attendance.photos")
public class AttendancePhotoProperties {
    /**
     * When set, attendance selfies are uploaded to this S3 bucket under {@link #keyPrefix}.
     * When empty, photos are stored under {@link #storageDir} (dev / fallback).
     */
    private String s3Bucket = "";
    private String keyPrefix = "attendance/";
    /** Local directory for attendance selfies when S3 is not configured. */
    private String storageDir = "data/attendance-photos";
    private String awsRegion = "ap-south-1";
    private long maxBytes = 1048576;
}
