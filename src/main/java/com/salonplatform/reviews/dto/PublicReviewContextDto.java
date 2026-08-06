package com.salonplatform.reviews.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PublicReviewContextDto {
    private String branchName;
    private String customerFirstName;
    private String status;
    private boolean alreadySubmitted;
    private Integer submittedRating;
    private String googleReviewUrl;
    private boolean googleReviewAutoPublish;
    private int googleAutoPublishMinRating;
    private List<String> improvementTagOptions;
    private List<ReviewCategoryOptionDto> categoryOptions;
}
