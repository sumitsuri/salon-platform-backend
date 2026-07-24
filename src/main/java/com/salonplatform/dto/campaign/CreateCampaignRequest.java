package com.salonplatform.dto.campaign;

import com.salonplatform.domain.enums.MessageChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateCampaignRequest {
    @NotBlank
    @Size(max = 120)
    private String name;

    @NotNull
    private MessageChannel channel;

    @NotBlank
    @Size(max = 500)
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
}
