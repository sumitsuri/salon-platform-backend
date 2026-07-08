package com.salonplatform.dto.user;

import lombok.Data;

@Data
public class UpdatePlatformUserRequest {
    private String name;
    private String email;
    private String password;
    private com.salonplatform.domain.enums.UserRole role;
    private java.util.UUID branchId;
    private Boolean active;
}
