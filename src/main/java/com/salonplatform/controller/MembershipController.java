package com.salonplatform.controller;

import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.dto.ApiResponse;
import com.salonplatform.dto.membership.CreateMembershipPlanRequest;
import com.salonplatform.dto.membership.MembershipPlanResponse;
import com.salonplatform.dto.membership.MembershipSubscriptionResponse;
import com.salonplatform.dto.membership.SellMembershipRequest;
import com.salonplatform.service.MembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @GetMapping("/plans")
    public ApiResponse<List<MembershipPlanResponse>> listPlans() {
        return ApiResponse.ok(membershipService.listPlans());
    }

    @GetMapping("/plans/active")
    public ApiResponse<List<MembershipPlanResponse>> listActivePlans() {
        return ApiResponse.ok(membershipService.listActivePlans());
    }

    @PostMapping("/plans")
    public ApiResponse<MembershipPlanResponse> createPlan(@Valid @RequestBody CreateMembershipPlanRequest request) {
        return ApiResponse.ok(membershipService.createPlan(request));
    }

    @PatchMapping("/plans/{id}/status")
    public ApiResponse<MembershipPlanResponse> planStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(membershipService.updatePlanStatus(id, PromoStatus.valueOf(body.get("status"))));
    }

    @PostMapping("/subscriptions")
    public ApiResponse<MembershipSubscriptionResponse> sell(@Valid @RequestBody SellMembershipRequest request) {
        return ApiResponse.ok(membershipService.sell(request));
    }

    @GetMapping("/customers/{customerId}/active")
    public ApiResponse<MembershipSubscriptionResponse> activeForCustomer(@PathVariable UUID customerId) {
        return ApiResponse.ok(membershipService.getActiveForCustomer(customerId));
    }
}
