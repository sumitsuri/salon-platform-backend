package com.salonplatform.reviews.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewCategoryOptionDto {
    private String id;
    private String label;
}
