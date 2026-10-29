package com.salonplatform.dto.offerposter;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PosterResponse {
    private UUID id;
    private UUID offerId;
    /** PENDING | READY | FAILED */
    private String status;
    private String style;
    private String format;
    /** openai | preview — "preview" means a local stand-in image, not AI art. */
    private String provider;
    /** Owner-facing reason when status is FAILED. */
    private String error;
    private Instant createdAt;
    private Instant completedAt;
}
