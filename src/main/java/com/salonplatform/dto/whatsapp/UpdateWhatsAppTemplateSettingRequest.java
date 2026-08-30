package com.salonplatform.dto.whatsapp;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateWhatsAppTemplateSettingRequest {
    private boolean active;
    /** Null = tenant-wide setting. */
    private UUID branchId;
}
