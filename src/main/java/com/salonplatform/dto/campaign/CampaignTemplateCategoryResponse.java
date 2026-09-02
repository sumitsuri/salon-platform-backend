package com.salonplatform.dto.campaign;

import com.salonplatform.domain.enums.CampaignTemplateCategoryCode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CampaignTemplateCategoryResponse {
    private CampaignTemplateCategoryCode code;
    private String label;
    private String description;
    private List<CampaignTemplateResponse> templates;
}
