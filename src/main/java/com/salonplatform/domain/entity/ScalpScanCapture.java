package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.ScalpCaptureZone;
import com.salonplatform.domain.enums.ScalpLightMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scalp_scan_captures", indexes = {
        @Index(name = "idx_scalp_capture_session", columnList = "session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScalpScanCapture {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ScalpCaptureZone zone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ScalpLightMode lightMode;

    @Column(nullable = false, length = 512)
    private String imageKey;

    @CreationTimestamp
    private Instant capturedAt;
}
