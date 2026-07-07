package com.salonplatform.dto.user;

import com.salonplatform.domain.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PlatformUserResponse {
    private UUID id;
    private String name;
    private String email;
    private UserRole role;
    private UUID branchId;
    private String branchName;
    private boolean active;
}
