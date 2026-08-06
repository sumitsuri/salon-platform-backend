package com.salonplatform.google;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleSearchRankEntry {
    private String keyword;
    private Integer yourRank;
    /** True when synced but branch was not found in the top 20 text results. */
    private Boolean yourRankBeyondTop20;
    /** Legacy string summary for older cached rows. */
    private List<String> topThree;
    private List<GoogleRankedPlace> topThreePlaces;
}
