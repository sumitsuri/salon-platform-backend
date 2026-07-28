package com.salonplatform.controller;

import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.promo.ApplicablePromoResponse;
import com.salonplatform.dto.promo.CouponResponse;
import com.salonplatform.dto.promo.CreateCouponRequest;
import com.salonplatform.dto.promo.CreateOfferRequest;
import com.salonplatform.dto.promo.OfferResponse;
import com.salonplatform.service.CouponService;
import com.salonplatform.service.OfferService;
import com.salonplatform.service.PromoResolutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final CouponService couponService;
    private final OfferService offerService;
    private final PromoResolutionService promoResolutionService;

    @GetMapping("/coupons")
    public ApiResponse<List<CouponResponse>> listCoupons() {
        return ApiResponse.ok(couponService.list());
    }

    @PostMapping("/coupons")
    public ApiResponse<CouponResponse> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        return ApiResponse.ok(couponService.create(request));
    }

    @PatchMapping("/coupons/{id}/status")
    public ApiResponse<CouponResponse> couponStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(couponService.updateStatus(id, PromoStatus.valueOf(body.get("status"))));
    }

    @GetMapping("/offers")
    public ApiResponse<List<OfferResponse>> listOffers() {
        return ApiResponse.ok(offerService.list());
    }

    @PostMapping("/offers")
    public ApiResponse<OfferResponse> createOffer(@Valid @RequestBody CreateOfferRequest request) {
        return ApiResponse.ok(offerService.create(request));
    }

    @PatchMapping("/offers/{id}/status")
    public ApiResponse<OfferResponse> offerStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(offerService.updateStatus(id, PromoStatus.valueOf(body.get("status"))));
    }

    @GetMapping("/applicable")
    public ApiResponse<List<ApplicablePromoResponse>> applicable(@RequestParam UUID branchId) {
        return ApiResponse.ok(promoResolutionService.listApplicable(branchId));
    }
}
