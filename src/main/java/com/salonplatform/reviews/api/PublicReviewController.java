package com.salonplatform.reviews.api;

import com.salonplatform.dto.ApiResponse;
import com.salonplatform.reviews.application.ReviewSubmissionService;
import com.salonplatform.reviews.dto.PublicReviewContextDto;
import com.salonplatform.reviews.dto.SubmitPublicReviewRequest;
import com.salonplatform.reviews.dto.SubmitPublicReviewResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/reviews")
@RequiredArgsConstructor
public class PublicReviewController {

    private final ReviewSubmissionService reviewSubmissionService;

    @GetMapping("/context")
    public ApiResponse<PublicReviewContextDto> context(@RequestParam String token) {
        return ApiResponse.ok(reviewSubmissionService.getContext(token));
    }

    @PostMapping
    public ApiResponse<SubmitPublicReviewResponse> submit(@Valid @RequestBody SubmitPublicReviewRequest request) {
        return ApiResponse.ok("Review submitted", reviewSubmissionService.submit(request));
    }
}
