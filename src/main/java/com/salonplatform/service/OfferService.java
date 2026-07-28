package com.salonplatform.service;

import com.salonplatform.domain.entity.Offer;
import com.salonplatform.domain.enums.DiscountType;
import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.domain.enums.ServiceScopeType;
import com.salonplatform.domain.repository.OfferRepository;
import com.salonplatform.dto.promo.CreateOfferRequest;
import com.salonplatform.dto.promo.OfferResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.util.PromoScopeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OfferService {

    private final OfferRepository offerRepository;

    @Transactional
    public OfferResponse create(CreateOfferRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        validate(request);

        Offer offer = Offer.builder()
                .tenantId(tenantId)
                .name(request.getName().trim())
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .serviceScope(request.getServiceScope() != null ? request.getServiceScope() : ServiceScopeType.ALL)
                .scopeIds(PromoScopeUtils.joinIds(request.getScopeIds()))
                .branchIds(PromoScopeUtils.joinIds(request.getBranchIds()))
                .status(request.getStatus() != null ? request.getStatus() : PromoStatus.ACTIVE)
                .maxRedemptionsTotal(request.getMaxRedemptionsTotal())
                .createdByUserId(SecurityUtils.currentUser().getId())
                .build();
        return toResponse(offerRepository.save(offer));
    }

    public List<OfferResponse> list() {
        SecurityUtils.assertBrandAdminOrAbove();
        return offerRepository.findByTenantIdOrderByCreatedAtDesc(SecurityUtils.requireTenantId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OfferResponse updateStatus(UUID id, PromoStatus status) {
        SecurityUtils.assertBrandAdminOrAbove();
        Offer offer = load(id);
        offer.setStatus(status);
        return toResponse(offerRepository.save(offer));
    }

    public Offer load(UUID id) {
        Offer offer = offerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        if (!offer.getTenantId().equals(SecurityUtils.requireTenantId())) {
            throw new ResourceNotFoundException("Offer not found");
        }
        return offer;
    }

    private void validate(CreateOfferRequest request) {
        if (!request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new BadRequestException("Offer end must be after start");
        }
        if (request.getDiscountType() == DiscountType.PERCENT
                && request.getDiscountValue().compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("Percent discount cannot exceed 100");
        }
    }

    private OfferResponse toResponse(Offer o) {
        return OfferResponse.builder()
                .id(o.getId())
                .name(o.getName())
                .description(o.getDescription())
                .discountType(o.getDiscountType())
                .discountValue(o.getDiscountValue())
                .startsAt(o.getStartsAt())
                .endsAt(o.getEndsAt())
                .serviceScope(o.getServiceScope())
                .scopeIds(PromoScopeUtils.parseIds(o.getScopeIds()))
                .branchIds(PromoScopeUtils.parseIds(o.getBranchIds()))
                .status(o.getStatus())
                .maxRedemptionsTotal(o.getMaxRedemptionsTotal())
                .redemptionCount(o.getRedemptionCount())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
