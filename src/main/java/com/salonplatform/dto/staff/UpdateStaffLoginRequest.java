package com.salonplatform.dto.staff;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateStaffLoginRequest {
    @Email
    private String email;

    @Size(min = 6, max = 72)
    private String password;

    private String designation;
}
