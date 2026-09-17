package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.*;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.booking.ApplyPromoRequest;
import com.salonplatform.dto.booking.BookingResponse;
import com.salonplatform.dto.scratch.*;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.util.PromoScopeUtils;
import com.salonplatform.util.VisitPassUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScratchFootfallService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String REDEEM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ScratchCampaignRepository campaignRepository;
    private final ScratchCardRepository cardRepository;
    private final CouponRepository couponRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final CustomerRepository customerRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Value("${app.public-frontend-base-url:http://localhost:3000}")
    private String publicFrontendBaseUrl;

    @Transactional
    public ScratchCampaignResponse createCampaign(CreateScratchCampaignRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        validateCampaignWindow(request.getStartsAt(), request.getEndsAt());
        validatePrizes(request.getPrizes());

        ScratchCampaign campaign = ScratchCampaign.builder()
                .tenantId(tenantId)
                .name(request.getName().trim())
                .description(request.getDescription())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .cardsValidDays(request.getCardsValidDays() != null ? request.getCardsValidDays() : 14)
                .status(request.getStatus() != null ? request.getStatus() : PromoStatus.ACTIVE)
                .createdByUserId(SecurityUtils.currentUserId())
                .build();

        int order = 0;
        for (ScratchCampaignPrizeRequest prizeReq : request.getPrizes()) {
            campaign.getPrizes().add(toPrizeEntity(prizeReq, campaign, order++));
        }
        return toCampaignResponse(campaignRepository.save(campaign), 0, 0);
    }

    public List<ScratchCampaignResponse> listCampaigns() {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        return campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(c -> toCampaignResponse(c, countIssued(c.getId()), countRedeemed(c.getId())))
                .collect(Collectors.toList());
    }

    public List<ScratchCampaignResponse> listActiveCampaignsForBranch(UUID branchId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBranchAccess(branchId);
        if (!isScratchCardEnabled(branchId)) {
            return List.of();
        }
        Instant now = Instant.now();
        return campaignRepository.findByTenantIdAndStatus(tenantId, PromoStatus.ACTIVE).stream()
                .filter(c -> !now.isBefore(c.getStartsAt()) && !now.isAfter(c.getEndsAt()))
                .map(c -> toCampaignResponse(c, countIssued(c.getId()), countRedeemed(c.getId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public ScratchCampaignResponse updateCampaignStatus(UUID id, PromoStatus status) {
        SecurityUtils.assertBrandAdminOrAbove();
        ScratchCampaign campaign = loadCampaign(id);
        campaign.setStatus(status);
        return toCampaignResponse(campaignRepository.save(campaign), countIssued(id), countRedeemed(id));
    }

    @Transactional
    public IssueScratchCardResponse issueCard(IssueScratchCardRequest request) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBranchAccess(request.getBranchId());
        assertScratchCardEnabled(request.getBranchId());
        ScratchCampaign campaign = loadCampaign(request.getCampaignId());
        assertCampaignRunnable(campaign);

        if (request.getBookingId() != null) {
            Booking booking = bookingRepository.findById(request.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
            if (!booking.getTenantId().equals(tenantId) || !booking.getBranchId().equals(request.getBranchId())) {
                throw new BadRequestException("Booking does not match branch");
            }
            Optional<ScratchCard> existingForBill = cardRepository.findTopByTenantIdAndBookingIdOrderByCreatedAtAsc(
                    tenantId, request.getBookingId());
            if (existingForBill.isPresent()) {
                ScratchCard existing = existingForBill.get();
                expireIfNeeded(existing);
                return toIssueResponse(existing, loadCampaignForTenant(existing.getCampaignId(), tenantId));
            }
        }

        Instant expiresAt = Instant.now().plus(campaign.getCardsValidDays(), ChronoUnit.DAYS);
        String token = VisitPassUtils.generatePublicToken();
        ScratchCard card = ScratchCard.builder()
                .tenantId(tenantId)
                .campaignId(campaign.getId())
                .branchId(request.getBranchId())
                .publicToken(token)
                .bookingId(request.getBookingId())
                .status(ScratchCardStatus.ISSUED)
                .expiresAt(expiresAt)
                .issuedByUserId(SecurityUtils.currentUserId())
                .build();
        cardRepository.save(card);

        return toIssueResponse(card, campaign);
    }

    public IssueScratchCardResponse findCardForBooking(UUID bookingId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        if (!booking.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Booking not found");
        }
        return cardRepository.findTopByTenantIdAndBookingIdOrderByCreatedAtAsc(tenantId, bookingId)
                .map(card -> {
                    expireIfNeeded(card);
                    ScratchCampaign campaign = loadCampaignForTenant(card.getCampaignId(), card.getTenantId());
                    return toIssueResponse(card, campaign);
                })
                .orElse(null);
    }

    private IssueScratchCardResponse toIssueResponse(ScratchCard card, ScratchCampaign campaign) {
        String scratchUrl = publicFrontendBaseUrl.replaceAll("/$", "")
                + "/scratch?token="
                + card.getPublicToken();
        return IssueScratchCardResponse.builder()
                .cardId(card.getId())
                .publicToken(card.getPublicToken())
                .scratchUrl(scratchUrl)
                .campaignName(campaign.getName())
                .status(card.getStatus())
                .build();
    }

    public PublicScratchCardResponse getPublicCard(String token) {
        ScratchCard card = loadCardByToken(token);
        expireIfNeeded(card);
        ScratchCampaign campaign = loadCampaignForTenant(card.getCampaignId(), card.getTenantId());
        Branch branch = branchRepository.findById(card.getBranchId()).orElse(null);
        Tenant tenant = tenantRepository.findById(card.getTenantId()).orElse(null);
        ScratchCampaignPrize prize = resolvePrize(card, campaign);
        return PublicScratchCardResponse.builder()
                .tenantName(tenant != null ? tenant.getName() : null)
                .tenantLogoUrl(tenant != null ? tenant.getLogoUrl() : null)
                .primaryColor(tenant != null ? tenant.getPrimaryColor() : null)
                .branchName(branch != null ? branch.getName() : null)
                .campaignName(campaign.getName())
                .campaignDescription(campaign.getDescription())
                .status(card.getStatus())
                .scratched(card.getStatus() != ScratchCardStatus.ISSUED)
                .contactRequired(false)
                .redeemable(card.getStatus() == ScratchCardStatus.UNLOCKED && card.getCouponId() != null)
                .prizeLabel(prize != null ? prize.getLabel() : null)
                .prizeKind(prize != null ? prize.getPrizeKind() : null)
                .prizeHeadline(prize != null ? buildPrizeHeadline(prize) : null)
                .redemptionCode(card.getStatus() == ScratchCardStatus.UNLOCKED ? card.getRedemptionCode() : null)
                .expiresAt(card.getExpiresAt())
                .build();
    }

    @Transactional
    public PublicScratchCardResponse scratchPublicCard(String token) {
        ScratchCard card = loadCardByToken(token);
        expireIfNeeded(card);
        if (card.getStatus() == ScratchCardStatus.REDEEMED) {
            throw new BadRequestException("This reward was already used");
        }
        ScratchCampaign campaign = loadCampaignForTenant(card.getCampaignId(), card.getTenantId());

        if (card.getStatus() != ScratchCardStatus.ISSUED) {
            if (card.getStatus() == ScratchCardStatus.SCRATCHED && card.getRedemptionCode() == null) {
                ScratchCampaignPrize existing = resolvePrize(card, campaign);
                if (existing != null && existing.getPrizeKind() != ScratchPrizeKind.TRY_AGAIN) {
                    unlockBillableReward(card, campaign, existing);
                    cardRepository.save(card);
                }
            }
            return getPublicCard(token);
        }

        assertCampaignRunnable(campaign);
        ScratchCampaignPrize prize = pickPrize(campaign);
        card.setPrizeId(prize.getId());
        card.setScratchedAt(Instant.now());
        if (prize.getPrizeKind() == ScratchPrizeKind.TRY_AGAIN) {
            card.setStatus(ScratchCardStatus.SCRATCHED);
        } else {
            unlockBillableReward(card, campaign, prize);
        }
        cardRepository.save(card);
        return getPublicCard(token);
    }

    private void unlockBillableReward(ScratchCard card, ScratchCampaign campaign, ScratchCampaignPrize prize) {
        card.setRedemptionCode(generateRedemptionCode(card.getTenantId()));
        card.setStatus(ScratchCardStatus.UNLOCKED);
        card.setCouponId(createSingleUseCoupon(card, campaign, prize).getId());
    }

    @Transactional
    public PublicScratchCardResponse captureContact(String token, CaptureScratchContactRequest request) {
        ScratchCard card = loadCardByToken(token);
        expireIfNeeded(card);
        if (card.getStatus() == ScratchCardStatus.REDEEMED) {
            throw new BadRequestException("This reward was already used");
        }
        if (card.getStatus() == ScratchCardStatus.ISSUED) {
            throw new BadRequestException("Scratch the card first");
        }
        if (card.getStatus() == ScratchCardStatus.UNLOCKED) {
            return getPublicCard(token);
        }

        String phone = normalizePhone(request.getPhone());
        if (phone == null || phone.length() < 10) {
            throw new BadRequestException("Enter a valid mobile number");
        }

        ScratchCampaign campaign = loadCampaignForTenant(card.getCampaignId(), card.getTenantId());
        ScratchCampaignPrize prize = resolvePrize(card, campaign);
        if (prize == null) {
            throw new BadRequestException("Prize missing on card");
        }

        Customer customer = customerRepository.findByBranchIdAndPhone(card.getBranchId(), phone)
                .orElseGet(() -> createGuestCustomer(card, phone, request.getName()));

        card.setCustomerPhone(phone);
        card.setCustomerName(request.getName() != null && !request.getName().isBlank()
                ? request.getName().trim()
                : customer.getName());
        card.setCustomerId(customer.getId());
        card.setContactCapturedAt(Instant.now());
        card.setRedemptionCode(generateRedemptionCode(card.getTenantId()));
        card.setStatus(ScratchCardStatus.UNLOCKED);

        if (prize.getPrizeKind() != ScratchPrizeKind.TRY_AGAIN) {
            card.setCouponId(createSingleUseCoupon(card, campaign, prize).getId());
        }
        cardRepository.save(card);

        if (card.getBookingId() != null) {
            bookingRepository.findById(card.getBookingId()).ifPresent(booking -> {
                if (booking.getCustomerId() == null) {
                    booking.setCustomerId(customer.getId());
                    bookingRepository.save(booking);
                }
            });
        }

        return getPublicCard(token);
    }

    @Transactional
    public BookingResponse redeemOnBooking(RedeemScratchCardRequest request) {
        UUID tenantId = SecurityUtils.requireTenantId();
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        if (!booking.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Booking not found");
        }

        ScratchCard card = resolveCardForRedemption(tenantId, request);
        if (!isScratchCardEnabled(booking.getBranchId())
                && (card.getBookingId() == null || !card.getBookingId().equals(booking.getId()))) {
            throw new BadRequestException("Scratch cards are not enabled for this branch");
        }
        expireIfNeeded(card);
        if (card.getBookingId() != null && !card.getBookingId().equals(booking.getId())) {
            throw new BadRequestException("This scratch reward belongs to a different bill");
        }
        cardRepository
                .findTopByTenantIdAndBookingIdAndStatus(tenantId, booking.getId(), ScratchCardStatus.REDEEMED)
                .ifPresent(already -> {
                    if (!already.getId().equals(card.getId())) {
                        throw new BadRequestException("This bill already used its visit scratch card");
                    }
                });
        if (card.getStatus() != ScratchCardStatus.UNLOCKED) {
            throw new BadRequestException("Reward is not ready to apply");
        }
        if (card.getCouponId() == null) {
            throw new BadRequestException("This scratch reward has no billing discount — honor it manually");
        }

        ApplyPromoRequest promoRequest = new ApplyPromoRequest();
        promoRequest.setCouponId(card.getCouponId());
        BookingResponse updated = bookingService.applyPromo(booking.getId(), promoRequest);

        card.setStatus(ScratchCardStatus.REDEEMED);
        card.setRedeemedAt(Instant.now());
        card.setRedeemedBookingId(booking.getId());
        cardRepository.save(card);
        return updated;
    }

    private ScratchCard resolveCardForRedemption(UUID tenantId, RedeemScratchCardRequest request) {
        if (request.getCardId() != null) {
            ScratchCard card = cardRepository.findById(request.getCardId())
                    .orElseThrow(() -> new ResourceNotFoundException("Scratch card not found"));
            if (!card.getTenantId().equals(tenantId)) {
                throw new ResourceNotFoundException("Scratch card not found");
            }
            return card;
        }
        if (request.getRedemptionCode() == null || request.getRedemptionCode().isBlank()) {
            throw new BadRequestException("Enter redemption code");
        }
        return cardRepository.findByTenantIdAndRedemptionCodeIgnoreCase(
                        tenantId, request.getRedemptionCode().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid redemption code"));
    }

    private Customer createGuestCustomer(ScratchCard card, String phone, String name) {
        Tenant tenant = tenantRepository.findById(card.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Branch branch = branchRepository.findById(card.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        String displayName = name != null && !name.isBlank() ? name.trim() : "Guest";
        Customer customer = Customer.builder()
                .tenantId(card.getTenantId())
                .branchId(card.getBranchId())
                .name(displayName)
                .phone(phone)
                .visitPassId(VisitPassUtils.generateVisitPassId(tenant, branch.getCode()))
                .identityStatus(CustomerIdentityStatus.PHONE_VERIFIED)
                .passPublicToken(VisitPassUtils.generatePublicToken())
                .visitCount(0)
                .lifetimeSpend(BigDecimal.ZERO)
                .whatsappOptIn(true)
                .build();
        return customerRepository.save(customer);
    }

    private Coupon createSingleUseCoupon(ScratchCard card, ScratchCampaign campaign, ScratchCampaignPrize prize) {
        Instant endsAt = card.getExpiresAt();
        String code = "SCR-" + generateRedemptionCode(card.getTenantId());
        DiscountType discountType = prize.getDiscountType();
        BigDecimal discountValue = prize.getDiscountValue();

        if (prize.getPrizeKind() == ScratchPrizeKind.COMPLIMENTARY_SERVICE) {
            discountType = DiscountType.PERCENT;
            discountValue = new BigDecimal("100");
        } else if (prize.getPrizeKind() == ScratchPrizeKind.TRY_AGAIN) {
            throw new BadRequestException("No coupon for try again");
        }

        Coupon coupon = Coupon.builder()
                .tenantId(card.getTenantId())
                .name("Scratch: " + prize.getLabel())
                .code(code)
                .description("Visit scratch reward · " + campaign.getName())
                .discountType(discountType)
                .discountValue(discountValue)
                .startsAt(Instant.now())
                .endsAt(endsAt)
                .serviceScope(prize.getServiceScope() != null ? prize.getServiceScope() : ServiceScopeType.ALL)
                .scopeIds(prize.getScopeIds())
                .status(PromoStatus.ACTIVE)
                .maxRedemptionsTotal(1)
                .redemptionCount(0)
                .createdByUserId(card.getIssuedByUserId())
                .build();
        return couponRepository.save(coupon);
    }

    private ScratchCampaignPrize pickPrize(ScratchCampaign campaign) {
        List<ScratchCampaignPrize> prizes = campaign.getPrizes();
        if (prizes.isEmpty()) {
            throw new BadRequestException("Campaign has no prizes configured");
        }
        int total = prizes.stream().mapToInt(p -> Math.max(1, p.getWeight())).sum();
        int roll = RANDOM.nextInt(total);
        int cursor = 0;
        for (ScratchCampaignPrize prize : prizes) {
            cursor += Math.max(1, prize.getWeight());
            if (roll < cursor) {
                return prize;
            }
        }
        return prizes.get(prizes.size() - 1);
    }

    private ScratchCampaignPrize resolvePrize(ScratchCard card, ScratchCampaign campaign) {
        if (card.getPrizeId() == null) {
            return null;
        }
        return campaign.getPrizes().stream()
                .filter(p -> p.getId().equals(card.getPrizeId()))
                .findFirst()
                .orElse(null);
    }

    private String buildPrizeHeadline(ScratchCampaignPrize prize) {
        return switch (prize.getPrizeKind()) {
            case PERCENT_OFF -> prize.getDiscountValue().stripTrailingZeros().toPlainString() + "% off";
            case FLAT_OFF -> "₹" + prize.getDiscountValue().stripTrailingZeros().toPlainString() + " off";
            case COMPLIMENTARY_SERVICE -> prize.getLabel();
            case TRY_AGAIN -> "Better luck next visit!";
        };
    }

    private void assertCampaignRunnable(ScratchCampaign campaign) {
        if (campaign.getStatus() != PromoStatus.ACTIVE) {
            throw new BadRequestException("Campaign is not active");
        }
        Instant now = Instant.now();
        if (now.isBefore(campaign.getStartsAt()) || now.isAfter(campaign.getEndsAt())) {
            throw new BadRequestException("Campaign is outside its active dates");
        }
    }

    private void expireIfNeeded(ScratchCard card) {
        if (card.getStatus() == ScratchCardStatus.REDEEMED || card.getStatus() == ScratchCardStatus.EXPIRED) {
            return;
        }
        if (Instant.now().isAfter(card.getExpiresAt())) {
            card.setStatus(ScratchCardStatus.EXPIRED);
            cardRepository.save(card);
            throw new BadRequestException("This scratch card has expired");
        }
    }

    private ScratchCampaign loadCampaign(UUID id) {
        UUID tenantId = SecurityUtils.requireTenantId();
        return loadCampaignForTenant(id, tenantId);
    }

    private ScratchCampaign loadCampaignForTenant(UUID id, UUID tenantId) {
        ScratchCampaign campaign = campaignRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        campaign.getPrizes().size();
        return campaign;
    }

    private ScratchCard loadCardByToken(String token) {
        return cardRepository.findByPublicToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Scratch card not found"));
    }

    private long countIssued(UUID campaignId) {
        return cardRepository.countByCampaignId(campaignId);
    }

    private long countRedeemed(UUID campaignId) {
        return cardRepository.countByCampaignIdAndStatus(campaignId, ScratchCardStatus.REDEEMED);
    }

    private void validateCampaignWindow(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new BadRequestException("Campaign end must be after start");
        }
    }

    private void validatePrizes(List<ScratchCampaignPrizeRequest> prizes) {
        if (prizes == null || prizes.isEmpty()) {
            throw new BadRequestException("Add at least one prize");
        }
        int totalWeight = 0;
        for (ScratchCampaignPrizeRequest prize : prizes) {
            totalWeight += Math.max(1, prize.getWeight() != null ? prize.getWeight() : 1);
            if (prize.getPrizeKind() == ScratchPrizeKind.PERCENT_OFF
                    || prize.getPrizeKind() == ScratchPrizeKind.FLAT_OFF) {
                if (prize.getDiscountType() == null || prize.getDiscountValue() == null) {
                    throw new BadRequestException("Discount prizes need type and value");
                }
            }
        }
        if (totalWeight <= 0) {
            throw new BadRequestException("Prize weights must be positive");
        }
    }

    private ScratchCampaignPrize toPrizeEntity(
            ScratchCampaignPrizeRequest req, ScratchCampaign campaign, int sortOrder) {
        ScratchCampaignPrize prize = ScratchCampaignPrize.builder()
                .campaign(campaign)
                .label(req.getLabel().trim())
                .prizeKind(req.getPrizeKind())
                .discountType(req.getDiscountType())
                .discountValue(req.getDiscountValue())
                .serviceScope(req.getServiceScope())
                .scopeIds(PromoScopeUtils.joinIds(req.getScopeIds()))
                .weight(req.getWeight() != null ? req.getWeight() : 1)
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : sortOrder)
                .build();
        return prize;
    }

    private ScratchCampaignResponse toCampaignResponse(ScratchCampaign campaign, long issued, long redeemed) {
        List<ScratchCampaignPrizeResponse> prizes = campaign.getPrizes().stream()
                .map(this::toPrizeResponse)
                .collect(Collectors.toList());
        return ScratchCampaignResponse.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .description(campaign.getDescription())
                .status(campaign.getStatus())
                .startsAt(campaign.getStartsAt())
                .endsAt(campaign.getEndsAt())
                .cardsValidDays(campaign.getCardsValidDays())
                .prizes(prizes)
                .createdAt(campaign.getCreatedAt())
                .cardsIssued((int) issued)
                .cardsRedeemed((int) redeemed)
                .build();
    }

    private ScratchCampaignPrizeResponse toPrizeResponse(ScratchCampaignPrize prize) {
        return ScratchCampaignPrizeResponse.builder()
                .id(prize.getId())
                .label(prize.getLabel())
                .prizeKind(prize.getPrizeKind())
                .discountType(prize.getDiscountType())
                .discountValue(prize.getDiscountValue())
                .serviceScope(prize.getServiceScope())
                .scopeIds(PromoScopeUtils.parseIds(prize.getScopeIds()))
                .weight(prize.getWeight())
                .sortOrder(prize.getSortOrder())
                .build();
    }

    private String generateRedemptionCode(UUID tenantId) {
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder code = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                code.append(REDEEM_ALPHABET.charAt(RANDOM.nextInt(REDEEM_ALPHABET.length())));
            }
            String candidate = code.toString();
            if (cardRepository.findByTenantIdAndRedemptionCodeIgnoreCase(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BadRequestException("Could not generate redemption code");
    }

    private static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() >= 10) {
            return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
        }
        return digits.isEmpty() ? null : digits;
    }

    private boolean isScratchCardEnabled(UUID branchId) {
        return branchRepository.findById(branchId)
                .map(b -> Boolean.TRUE.equals(b.getScratchCardEnabled()))
                .orElse(false);
    }

    private void assertScratchCardEnabled(UUID branchId) {
        if (!isScratchCardEnabled(branchId)) {
            throw new BadRequestException("Scratch cards are not enabled for this branch");
        }
    }
}
