package com.salonplatform.service;

import com.salonplatform.campaign.CampaignTemplateCatalog;
import com.salonplatform.campaign.CampaignTemplateDefinition;
import com.salonplatform.campaign.CampaignTemplateFilterPreset;
import com.salonplatform.domain.entity.SalonService;
import com.salonplatform.domain.entity.ServiceCategory;
import com.salonplatform.domain.enums.CampaignTemplateCategoryCode;
import com.salonplatform.domain.repository.SalonServiceRepository;
import com.salonplatform.domain.repository.ServiceCategoryRepository;
import com.salonplatform.dto.campaign.CampaignTemplateCategoryResponse;
import com.salonplatform.dto.campaign.CampaignTemplateFilterDto;
import com.salonplatform.dto.campaign.CampaignTemplateLibraryResponse;
import com.salonplatform.dto.campaign.CampaignTemplateResponse;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampaignTemplateService {

    private final SalonServiceRepository salonServiceRepository;
    private final ServiceCategoryRepository categoryRepository;

    public CampaignTemplateLibraryResponse listLibrary() {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        List<SalonService> services = salonServiceRepository.findByTenantIdAndActiveTrue(tenantId);
        List<ServiceCategory> categories = categoryRepository.findByTenantIdAndActiveTrueOrderBySortOrderAsc(tenantId);

        List<CampaignTemplateCategoryResponse> categoryResponses = CampaignTemplateCatalog.categoryOrder().stream()
                .map(code -> CampaignTemplateCategoryResponse.builder()
                        .code(code)
                        .label(categoryLabel(code))
                        .description(categoryDescription(code))
                        .templates(CampaignTemplateCatalog.byCategory(code).stream()
                                .map(def -> toResponse(def, services, categories))
                                .toList())
                        .build())
                .toList();

        return CampaignTemplateLibraryResponse.builder()
                .categories(categoryResponses)
                .build();
    }

    public CampaignTemplateResponse getTemplate(String templateId) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        CampaignTemplateDefinition def = CampaignTemplateCatalog.findById(templateId)
                .orElseThrow(() -> new com.salonplatform.exception.ResourceNotFoundException("error.campaign.templateNotFound"));
        List<SalonService> services = salonServiceRepository.findByTenantIdAndActiveTrue(tenantId);
        List<ServiceCategory> categories = categoryRepository.findByTenantIdAndActiveTrueOrderBySortOrderAsc(tenantId);
        return toResponse(def, services, categories);
    }

    private CampaignTemplateResponse toResponse(
            CampaignTemplateDefinition def,
            List<SalonService> services,
            List<ServiceCategory> categories) {
        CampaignTemplateFilterPreset preset = def.getFilterPreset();
        return CampaignTemplateResponse.builder()
                .id(def.getId())
                .category(def.getCategory())
                .categoryLabel(categoryLabel(def.getCategory()))
                .name(def.getName())
                .description(def.getDescription())
                .goal(def.getGoal())
                .suggestedMessage(def.getSuggestedMessage())
                .filters(resolveFilters(preset, services, categories))
                .build();
    }

    private CampaignTemplateFilterDto resolveFilters(
            CampaignTemplateFilterPreset preset,
            List<SalonService> services,
            List<ServiceCategory> categories) {
        if (preset == null) {
            return CampaignTemplateFilterDto.builder().build();
        }
        LocalDate today = LocalDate.now();
        return CampaignTemplateFilterDto.builder()
                .minVisitCount(preset.getMinVisitCount())
                .maxVisitCount(preset.getMaxVisitCount())
                .minLifetimeSpend(preset.getMinLifetimeSpend())
                .maxLifetimeSpend(preset.getMaxLifetimeSpend())
                .lastVisitFrom(preset.getLastVisitFromDaysAgo() != null
                        ? today.minusDays(preset.getLastVisitFromDaysAgo()).toString() : null)
                .lastVisitTo(preset.getLastVisitToDaysAgo() != null
                        ? today.minusDays(preset.getLastVisitToDaysAgo()).toString() : null)
                .membershipFilter(preset.getMembershipFilter())
                .membershipExpiringWithinDays(preset.getMembershipExpiringWithinDays())
                .hasServiceIds(resolveServiceIds(services, preset.getServiceKeywords()))
                .excludeServiceIds(resolveServiceIds(services, preset.getExcludeServiceKeywords()))
                .hasServiceCategoryIds(resolveCategoryIds(categories, preset.getServiceCategoryKeywords()))
                .excludeServiceCategoryIds(resolveCategoryIds(categories, preset.getExcludeServiceCategoryKeywords()))
                .maxOverallRating(preset.getMaxOverallRating())
                .minOverallRating(preset.getMinOverallRating())
                .hasSubmittedReview(preset.getHasSubmittedReview())
                .googleReviewNotSubmitted(preset.getGoogleReviewNotSubmitted())
                .bookingSource(preset.getBookingSource())
                .build();
    }

    private List<UUID> resolveServiceIds(List<SalonService> services, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (SalonService svc : services) {
            if (matchesAnyKeyword(svc.getName(), keywords)) {
                ids.add(svc.getId());
            }
        }
        return new ArrayList<>(ids);
    }

    private List<UUID> resolveCategoryIds(List<ServiceCategory> categories, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (ServiceCategory cat : categories) {
            if (matchesAnyKeyword(cat.getName(), keywords)) {
                ids.add(cat.getId());
            }
        }
        return new ArrayList<>(ids);
    }

    private boolean matchesAnyKeyword(String text, List<String> keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String categoryLabel(CampaignTemplateCategoryCode code) {
        return switch (code) {
            case WINBACK -> "Win-back & retention";
            case MEMBERSHIP -> "Membership growth";
            case PREMIUM_UPSELL -> "Premium service upsell";
            case CROSS_SELL -> "Cross-sell bundles";
            case REVIEWS -> "Reviews & reputation";
            case VIP_BEHAVIOURAL -> "VIP & behavioural";
        };
    }

    private String categoryDescription(CampaignTemplateCategoryCode code) {
        return switch (code) {
            case WINBACK -> "Bring back lapsed and at-risk customers";
            case MEMBERSHIP -> "Convert visitors into members and renew expiring plans";
            case PREMIUM_UPSELL -> "Introduce high-margin premium services";
            case CROSS_SELL -> "Bundle complementary services for higher ticket size";
            case REVIEWS -> "Recover detractors and turn promoters into advocates";
            case VIP_BEHAVIOURAL -> "Reward top spenders and shift visit patterns";
        };
    }
}
