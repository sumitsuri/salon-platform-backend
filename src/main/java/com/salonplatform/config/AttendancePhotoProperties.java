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
    /** Local directory for attendance selfies (default). */
    private String storageDir = "data/attendance-photos";
    private long maxBytes = 1048576;
}
