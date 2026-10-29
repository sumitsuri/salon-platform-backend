package com.salonplatform.service;

import com.salonplatform.config.OfferPosterProperties;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.FestiveOffer;
import com.salonplatform.domain.entity.OfferPoster;
import com.salonplatform.domain.enums.OfferPosterStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.FestiveOfferRepository;
import com.salonplatform.domain.repository.OfferPosterRepository;
import com.salonplatform.dto.offerposter.*;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.offerposter.*;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Festive offers owners turn into WhatsApp-ready posters. Brand-admin only; marketing content, not billing. */
@Service
@RequiredArgsConstructor
@Slf4j
public class FestiveOfferService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\-\\s]{8,25}$");
    private static final Duration STALE_PENDING = Duration.ofMinutes(10);
    private static final BigDecimal MAX_MONEY = new BigDecimal("10000000");

    private final FestiveOfferRepository offerRepository;
    private final OfferPosterRepository posterRepository;
    private final BranchRepository branchRepository;
    private final FestiveOfferMapper mapper;
    private final PosterImageProviders providers;
    private final OfferPosterWorker worker;
    private final OfferPosterStorageService storage;
    private final OfferPosterProperties properties;
    private final AuditService auditService;

    // ── Studio info ────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PosterStudioInfo studioInfo() {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        return PosterStudioInfo.builder()
                .provider(providers.activeName())
                .dailyLimit(properties.getDailyLimitPerTenant())
                .usedToday(usedToday(tenantId))
                .occasions(Arrays.stream(PosterOccasion.values()).map(o -> PosterStudioInfo.Option.builder()
                        .id(o.name()).label(o.label()).emoji(o.emoji())
                        .suggestedHeadline(o.suggestedHeadline()).suggestedTagline(o.suggestedTagline()).build()).toList())
                .styles(Arrays.stream(PosterStyle.values()).map(s -> PosterStudioInfo.Option.builder()
                        .id(s.name()).label(s.label()).description(s.description()).build()).toList())
                .formats(Arrays.stream(PosterFormat.values()).map(f -> PosterStudioInfo.Option.builder()
                        .id(f.name()).label(f.label()).build()).toList())
                .build();
    }

    // ── Offers ─────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FestiveOfferResponse> list() {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        List<FestiveOffer> offers = offerRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
        return toResponses(offers, tenantId);
    }

    @Transactional(readOnly = true)
    public FestiveOfferResponse get(UUID id) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        return toResponses(List.of(requireOffer(tenantId, id)), tenantId).get(0);
    }

    @Transactional
    public FestiveOfferResponse create(FestiveOfferRequest request) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        requireBranch(tenantId, request.getBranchId());
        FestiveOffer offer = FestiveOffer.builder().tenantId(tenantId).createdBy(SecurityUtils.currentUser().getId()).build();
        apply(offer, request);
        offer = offerRepository.save(offer);
        auditService.log("FESTIVE_OFFER_CREATED", "FestiveOffer", offer.getId(), offer.getHeadline());
        return toResponses(List.of(offer), tenantId).get(0);
    }

    @Transactional
    public FestiveOfferResponse update(UUID id, FestiveOfferRequest request) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        FestiveOffer offer = requireOffer(tenantId, id);
        requireBranch(tenantId, request.getBranchId());
        apply(offer, request);
        offer = offerRepository.save(offer);
        auditService.log("FESTIVE_OFFER_UPDATED", "FestiveOffer", offer.getId(), offer.getHeadline());
        return toResponses(List.of(offer), tenantId).get(0);
    }

    @Transactional
    public void delete(UUID id) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        FestiveOffer offer = requireOffer(tenantId, id);
        List<String> keys = posterRepository.findByOfferId(id).stream()
                .map(OfferPoster::getImageKey).filter(Objects::nonNull).toList();
        posterRepository.deleteByOfferId(id);
        offerRepository.delete(offer);
        auditService.log("FESTIVE_OFFER_DELETED", "FestiveOffer", id, offer.getHeadline());
        runAfterCommit(() -> keys.forEach(storage::delete));
    }

    // ── Posters ────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public PosterResponse generatePoster(UUID offerId) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        FestiveOffer offer = requireOffer(tenantId, offerId);

        PosterImageProvider provider = providers.active().orElseThrow(() -> new BadRequestException(
                "AI poster generation is not set up yet. Ask your administrator to add the image service key."));

        failStalePending(offerId);
        if (posterRepository.existsByOfferIdAndStatus(offerId, OfferPosterStatus.PENDING)) {
            throw new BadRequestException("A poster is already being created for this offer. Please wait for it to finish.");
        }
        int limit = properties.getDailyLimitPerTenant();
        if (limit > 0 && usedToday(tenantId) >= limit) {
            throw new BadRequestException("You have reached today's limit of " + limit
                    + " posters. Please try again tomorrow.");
        }

        OfferContent content = mapper.toContent(offer);
        OfferPoster poster = posterRepository.save(OfferPoster.builder()
                .tenantId(tenantId)
                .offerId(offerId)
                .style(offer.getStyle())
                .format(offer.getFormat())
                .prompt(PosterPromptBuilder.build(content))
                .provider(provider.name())
                .model(provider.model())
                .createdBy(SecurityUtils.currentUser().getId())
                .build());
        auditService.log("OFFER_POSTER_REQUESTED", "OfferPoster", poster.getId(), offer.getHeadline() + " via " + provider.name());
        UUID posterId = poster.getId();
        runAfterCommit(() -> worker.generate(posterId));
        return toPosterResponse(poster);
    }

    @Transactional
    public PosterResponse getPoster(UUID posterId) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        OfferPoster poster = requirePoster(tenantId, posterId);
        failStalePending(poster.getOfferId());
        return toPosterResponse(posterRepository.findById(posterId).orElse(poster));
    }

    @Transactional
    public List<PosterResponse> listPosters(UUID offerId) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        requireOffer(tenantId, offerId);
        failStalePending(offerId);
        return posterRepository.findByOfferIdAndTenantIdOrderByCreatedAtDesc(offerId, tenantId).stream()
                .map(this::toPosterResponse).toList();
    }

    public record PosterImage(byte[] bytes, String contentType) {}

    @Transactional(readOnly = true)
    public PosterImage loadPosterImage(UUID posterId) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        OfferPoster poster = requirePoster(tenantId, posterId);
        if (poster.getStatus() != OfferPosterStatus.READY || poster.getImageKey() == null) {
            throw new ResourceNotFoundException("Poster image is not ready");
        }
        return new PosterImage(storage.load(poster.getImageKey()),
                poster.getContentType() != null ? poster.getContentType() : "image/jpeg");
    }

    @Transactional
    public void deletePoster(UUID posterId) {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        OfferPoster poster = requirePoster(tenantId, posterId);
        String key = poster.getImageKey();
        posterRepository.delete(poster);
        runAfterCommit(() -> storage.delete(key));
    }

    // ── Internals ──────────────────────────────────────────────────────────────────────────────

    private void failStalePending(UUID offerId) {
        Instant cutoff = Instant.now().minus(STALE_PENDING);
        for (OfferPoster p : posterRepository.findByOfferId(offerId)) {
            if (p.getStatus() == OfferPosterStatus.PENDING && p.getCreatedAt() != null && p.getCreatedAt().isBefore(cutoff)) {
                p.setStatus(OfferPosterStatus.FAILED);
                p.setError("This poster took too long and was stopped. Please try again.");
                p.setCompletedAt(Instant.now());
                posterRepository.save(p);
            }
        }
    }

    private int usedToday(UUID tenantId) {
        Instant dayStart = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        return (int) posterRepository.countByTenantIdAndCreatedAtGreaterThanEqual(tenantId, dayStart);
    }

    private FestiveOffer requireOffer(UUID tenantId, UUID id) {
        return offerRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
    }

    private OfferPoster requirePoster(UUID tenantId, UUID id) {
        return posterRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Poster not found"));
    }

    private Branch requireBranch(UUID tenantId, UUID branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BadRequestException("Choose a valid branch"));
        if (!tenantId.equals(branch.getTenantId())) {
            throw new BadRequestException("Choose a valid branch");
        }
        return branch;
    }

    private void apply(FestiveOffer offer, FestiveOfferRequest r) {
        PosterOccasion occasion = parse(PosterOccasion.class, r.getOccasion(), "occasion");
        PosterStyle style = parse(PosterStyle.class, r.getStyle(), "style");
        PosterFormat format = parse(PosterFormat.class, r.getFormat(), "format");

        if (r.getValidUntil() != null && r.getValidUntil().isBefore(LocalDate.now(ZONE))) {
            throw new BadRequestException("The offer's end date is in the past.");
        }
        String phone = clean(r.getPhone());
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw new BadRequestException("Enter a valid phone number for bookings.");
        }
        List<OfferBlockDto> blocks = normaliseBlocks(r.getBlocks());
        List<String> included = r.getIncludedServices() == null ? List.of() : r.getIncludedServices().stream()
                .map(FestiveOfferService::clean).filter(Objects::nonNull).limit(8).collect(Collectors.toList());

        offer.setBranchId(r.getBranchId());
        offer.setOccasion(occasion.name());
        offer.setStyle(style.name());
        offer.setFormat(format.name());
        offer.setHeadline(r.getHeadline().trim());
        offer.setTagline(clean(r.getTagline()));
        offer.setBrandName(r.getBrandName().trim());
        offer.setAddress(clean(r.getAddress()));
        offer.setPhone(phone);
        offer.setValidUntil(r.getValidUntil());
        offer.setTerms(clean(r.getTerms()));
        offer.setExtraDirection(clean(r.getExtraDirection()));
        offer.setBlocksJson(mapper.writeBlocks(blocks));
        offer.setIncludedServicesJson(mapper.writeStrings(included));

        String caption = clean(r.getCaption());
        offer.setCaption(caption != null ? caption : OfferCaptionBuilder.build(mapper.toContent(offer)));
    }

    static List<OfferBlockDto> normaliseBlocks(List<OfferBlockDto> in) {
        if (in == null || in.isEmpty()) {
            throw new BadRequestException("Add at least one offer to the poster.");
        }
        if (in.size() > 6) {
            throw new BadRequestException("A poster can show at most 6 offers.");
        }
        List<OfferBlockDto> out = new ArrayList<>();
        for (OfferBlockDto b : in) {
            OfferBlockDto n = new OfferBlockDto();
            n.setType(b.getType());
            n.setAppliesTo(limit(clean(b.getAppliesTo()), 60, "'applies to' text"));
            n.setTitle(limit(clean(b.getTitle()), 60, "package name"));
            switch (b.getType()) {
                case SPEND_PERCENT, FREE_WITH_SPEND -> {
                    List<OfferBlockDto.Tier> tiers = b.getTiers() == null ? List.of() : b.getTiers();
                    if (tiers.isEmpty() || tiers.size() > 5) {
                        throw new BadRequestException("Each spend offer needs between 1 and 5 tiers.");
                    }
                    Set<BigDecimal> seen = new HashSet<>();
                    for (OfferBlockDto.Tier t : tiers) {
                        if (t.getMinSpend() == null || t.getMinSpend().compareTo(BigDecimal.ONE) < 0
                                || t.getMinSpend().compareTo(MAX_MONEY) > 0) {
                            throw new BadRequestException("Enter a spend amount of at least ₹1 for every tier.");
                        }
                        if (!seen.add(t.getMinSpend().stripTrailingZeros())) {
                            throw new BadRequestException("Two tiers have the same spend amount.");
                        }
                        OfferBlockDto.Tier nt = new OfferBlockDto.Tier();
                        nt.setMinSpend(t.getMinSpend());
                        if (b.getType() == OfferBlockType.SPEND_PERCENT) {
                            requirePercent(t.getPercentOff(), "Each tier's discount");
                            nt.setPercentOff(t.getPercentOff());
                        } else {
                            String free = limit(clean(t.getFreeItem()), 80, "free item");
                            if (free == null) {
                                throw new BadRequestException("Say what the customer gets free in every tier.");
                            }
                            nt.setFreeItem(free);
                        }
                        n.getTiers().add(nt);
                    }
                }
                case PACKAGE -> {
                    List<OfferBlockDto.PackageItem> items = b.getItems() == null ? List.of() : b.getItems();
                    if (items.size() < 2 || items.size() > 12) {
                        throw new BadRequestException("A package needs between 2 and 12 services.");
                    }
                    requirePercent(b.getDiscountPercent(), "The package discount");
                    n.setDiscountPercent(b.getDiscountPercent());
                    for (OfferBlockDto.PackageItem i : items) {
                        String name = limit(clean(i.getName()), 80, "service name");
                        if (name == null) throw new BadRequestException("Every package service needs a name.");
                        if (i.getPrice() == null || i.getPrice().signum() <= 0 || i.getPrice().compareTo(MAX_MONEY) > 0) {
                            throw new BadRequestException("Every package service needs a price above ₹0.");
                        }
                        OfferBlockDto.PackageItem ni = new OfferBlockDto.PackageItem();
                        ni.setServiceId(i.getServiceId());
                        ni.setName(name);
                        ni.setPrice(i.getPrice());
                        n.getItems().add(ni);
                    }
                }
            }
            out.add(n);
        }
        return out;
    }

    private static void requirePercent(BigDecimal pct, String what) {
        if (pct == null || pct.compareTo(BigDecimal.ONE) < 0 || pct.compareTo(new BigDecimal("90")) > 0) {
            throw new BadRequestException(what + " must be between 1% and 90%.");
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Choose a valid " + label + ".");
        }
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static String limit(String s, int max, String what) {
        if (s != null && s.length() > max) {
            throw new BadRequestException("The " + what + " is too long (max " + max + " characters).");
        }
        return s;
    }

    private List<FestiveOfferResponse> toResponses(List<FestiveOffer> offers, UUID tenantId) {
        if (offers.isEmpty()) return List.of();
        Map<UUID, String> branchNames = branchRepository.findAllById(
                        offers.stream().map(FestiveOffer::getBranchId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));
        Map<UUID, List<OfferPoster>> postersByOffer = posterRepository
                .findByOfferIdInAndTenantIdOrderByCreatedAtDesc(
                        offers.stream().map(FestiveOffer::getId).toList(), tenantId).stream()
                .collect(Collectors.groupingBy(OfferPoster::getOfferId));

        List<FestiveOfferResponse> out = new ArrayList<>();
        for (FestiveOffer o : offers) {
            OfferContent c = mapper.toContent(o);
            List<OfferPoster> posters = postersByOffer.getOrDefault(o.getId(), List.of());
            out.add(FestiveOfferResponse.builder()
                    .id(o.getId())
                    .branchId(o.getBranchId())
                    .branchName(branchNames.get(o.getBranchId()))
                    .occasion(o.getOccasion())
                    .occasionLabel(c.occasion().label())
                    .headline(o.getHeadline())
                    .tagline(o.getTagline())
                    .brandName(o.getBrandName())
                    .address(o.getAddress())
                    .phone(o.getPhone())
                    .validUntil(o.getValidUntil())
                    .terms(o.getTerms())
                    .blocks(c.blocks())
                    .includedServices(c.includedServices())
                    .style(o.getStyle())
                    .format(o.getFormat())
                    .extraDirection(o.getExtraDirection())
                    .caption(o.getCaption())
                    .summaryLines(OfferCopy.summary(c.blocks()))
                    .latestPoster(posters.isEmpty() ? null : toPosterResponse(posters.get(0)))
                    .posterCount(posters.size())
                    .createdAt(o.getCreatedAt())
                    .updatedAt(o.getUpdatedAt())
                    .build());
        }
        return out;
    }

    private PosterResponse toPosterResponse(OfferPoster p) {
        return PosterResponse.builder()
                .id(p.getId())
                .offerId(p.getOfferId())
                .status(p.getStatus().name())
                .style(p.getStyle())
                .format(p.getFormat())
                .provider(p.getProvider())
                .error(p.getError())
                .createdAt(p.getCreatedAt())
                .completedAt(p.getCompletedAt())
                .build();
    }

    private static void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
