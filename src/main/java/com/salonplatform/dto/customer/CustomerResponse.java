package com.salonplatform.dto.customer;

import com.salonplatform.domain.enums.CustomerIdentityStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
public class CustomerResponse {
    private UUID id;
    private String name;
    private String phone;
    private String visitPassId;
    private CustomerIdentityStatus identityStatus;
    private String passPublicToken;
    private String email;
    private String society;
    private String flatUnit;
    private String notes;
    private Boolean whatsappOptIn;
    private Boolean smsOptIn;
    private Integer visitCount;
    private BigDecimal lifetimeSpend;
    private Instant lastVisitAt;
}
