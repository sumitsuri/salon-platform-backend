package com.salonplatform.dto.staff;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SuggestedStaffPasswordResponse {
    private String password;
}
