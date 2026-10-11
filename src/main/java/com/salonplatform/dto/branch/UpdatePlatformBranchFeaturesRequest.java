package com.salonplatform.dto.branch;

import lombok.Data;

/** Platform-only branch feature toggles; null fields are left unchanged. */
@Data
public class UpdatePlatformBranchFeaturesRequest {
    private Boolean localSpotlightGoogleSyncEnabled;
}
