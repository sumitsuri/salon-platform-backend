package com.salonplatform.dto.campaign;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CampaignTemplateLibraryResponse {
    private List<CampaignTemplateCategoryResponse> categories;
}
