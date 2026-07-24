package com.salonplatform.dto.campaign;

import com.salonplatform.domain.enums.CampaignStatus;
import com.salonplatform.domain.enums.MessageChannel;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class CampaignResponse {
    private UUID id;
    private String name;
    private MessageChannel channel;
    private CampaignStatus status;
    private String messageText;
    private String filterName;
    private String filterSociety;
    private String filterPhone;
    private Integer filterMinVisitCount;
    private Integer filterMaxVisitCount;
    private BigDecimal filterMinLifetimeSpend;
    private BigDecimal filterMaxLifetimeSpend;
    private LocalDate filterLastVisitFrom;
    private LocalDate filterLastVisitTo;
    private Boolean filterWhatsappOptInOnly;
    private Boolean filterSmsOptInOnly;
    private Integer recipientCount;
    private Integer sentCount;
    private Integer failedCount;
    private Instant sentAt;
    private Instant createdAt;
}
