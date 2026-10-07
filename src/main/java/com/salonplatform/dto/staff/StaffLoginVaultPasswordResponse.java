package com.salonplatform.dto.staff;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StaffLoginVaultPasswordResponse {
    private boolean available;
    private String password;
}
