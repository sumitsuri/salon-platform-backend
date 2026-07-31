package com.salonplatform.reviews.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SubmitPublicReviewRequest {
    @NotBlank
    private String token;
    @NotNull
    @Min(1)
    @Max(5)
    private Integer overallRating;
    private Map<String, Integer> categoryRatings;
    private List<String> improvementTags;
    private String comment;
    private boolean googleReviewRedirected;
}
