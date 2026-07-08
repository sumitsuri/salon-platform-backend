package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BranchRecommendations {
    private UUID branchId;
    private String branchName;
    private List<Recommendation> items;
}
