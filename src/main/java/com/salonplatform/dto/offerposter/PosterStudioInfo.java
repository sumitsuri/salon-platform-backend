package com.salonplatform.dto.offerposter;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Everything the offer studio screen needs that is not specific to one offer. */
@Data
@Builder
public class PosterStudioInfo {

    /** openai | preview | none */
    private String provider;
    private int dailyLimit;
    private int usedToday;
    private List<Option> occasions;
    private List<Option> styles;
    private List<Option> formats;

    @Data
    @Builder
    public static class Option {
        private String id;
        private String label;
        private String emoji;
        private String description;
        private String suggestedHeadline;
        private String suggestedTagline;
    }
}
