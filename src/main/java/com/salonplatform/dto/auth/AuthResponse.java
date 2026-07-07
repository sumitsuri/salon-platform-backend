package com.salonplatform.dto.auth;

import com.salonplatform.domain.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private UUID userId;
    private String name;
    private String email;
    private UserRole role;
    private UUID tenantId;
    private UUID branchId;
    private String tenantName;
    private String branchName;
    private String primaryColor;
    private String logoUrl;
}
