package com.salonplatform.dto.meta;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocaleInfoResponse {
    private String code;
    private String label;
    private String nativeLabel;
    private String stateCode;
    private String stateName;
    private String stateNameNative;
    private String regionGroup;
    private int sortOrder;
}
