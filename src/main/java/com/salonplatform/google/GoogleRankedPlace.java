package com.salonplatform.google;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleRankedPlace {
    private int rank;
    private String name;
    private String googlePlaceId;
    private String googleMapsUrl;
}
