package com.salonplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per signed-in device ("session"). Rows are removed on logout / revoke, so a row existing means the
 * session is live. {@code expiresAt} is null for sessions that never expire (everyone except platform staff).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String token;

    /** Null = never expires. Set only for roles that keep an idle limit (platform staff). */
    private Instant expiresAt;

    @Builder.Default
    private boolean revoked = false;

    @CreationTimestamp
    private Instant createdAt;

    /** Last time this device refreshed its session (written at most once an hour). */
    private Instant lastUsedAt;

    // ── Device metadata (parsed from the User-Agent at sign-in) ──
    @Column(length = 500)
    private String userAgent;

    @Column(length = 120)
    private String deviceLabel;

    @Column(length = 20)
    private String deviceType;

    @Column(length = 60)
    private String browser;

    @Column(length = 60)
    private String os;

    /** employee | manager | admin | platform — the app the user signed in to. */
    @Column(length = 20)
    private String portal;

    // ── Where the device signed in from, and where it was last seen ──
    @Column(length = 64)
    private String loginIp;

    @Column(length = 200)
    private String loginLocation;

    @Column(length = 64)
    private String lastIp;

    @Column(length = 200)
    private String lastLocation;
}
