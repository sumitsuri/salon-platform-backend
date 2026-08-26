package com.salonplatform.dto.campaign;

import com.salonplatform.dto.customer.CustomerResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CampaignPreviewResponse {
    private long matchingCustomers;
    /** Sample of matching customers (same filters as send), capped for UI preview. */
    private List<CustomerResponse> customers;
    /** True when {@link #matchingCustomers} exceeds the preview sample size. */
    private boolean previewTruncated;
}
