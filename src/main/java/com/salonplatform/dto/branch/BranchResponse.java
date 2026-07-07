package com.salonplatform.dto.branch;

import com.salonplatform.domain.enums.BranchStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BranchResponse {
    private UUID id;
    private String name;
    private String code;
    private String address;
    private String societyDefault;
    private String gstin;
    private String phone;
    private String openTime;
    private String closeTime;
    private BranchStatus status;
    private Instant createdAt;
}
