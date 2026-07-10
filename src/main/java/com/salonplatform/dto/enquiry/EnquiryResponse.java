package com.salonplatform.dto.enquiry;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class EnquiryResponse {
    private UUID id;
    private String name;
    private String society;
    private String email;
    private String mobile;
    private String message;
    private Instant createdAt;
}
