package com.salonplatform.campaign;

import com.salonplatform.domain.enums.CampaignTemplateCategoryCode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CampaignTemplateDefinition {
    private String id;
    private CampaignTemplateCategoryCode category;
    private String name;
    private String description;
    private String goal;
    private String suggestedMessage;
    private CampaignTemplateFilterPreset filterPreset;
}
