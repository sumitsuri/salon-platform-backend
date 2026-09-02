package com.salonplatform.dto.campaign;

import com.salonplatform.domain.enums.CampaignTemplateCategoryCode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CampaignTemplateResponse {
    private String id;
    private CampaignTemplateCategoryCode category;
    private String categoryLabel;
    private String name;
    private String description;
    private String goal;
    private String suggestedMessage;
    private CampaignTemplateFilterDto filters;
}
