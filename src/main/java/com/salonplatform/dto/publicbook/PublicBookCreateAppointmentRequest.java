package com.salonplatform.dto.publicbook;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class PublicBookCreateAppointmentRequest {
    private String phone;
    private String otp;
    private String customerName;
    private String society;
    private String flatUnit;
    /** @deprecated use branchServiceIds */
    private UUID branchServiceId;
    private List<UUID> branchServiceIds;
    private UUID staffId;
    private String startAt;
    private String note;
}
