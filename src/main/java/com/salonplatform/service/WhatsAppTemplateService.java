package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.TenantWhatsAppTemplateSetting;
import com.salonplatform.domain.enums.WhatsAppTemplateCategory;
import com.salonplatform.domain.enums.WhatsAppTemplateCode;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.TenantWhatsAppTemplateSettingRepository;
import com.salonplatform.dto.whatsapp.*;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.whatsapp.WhatsAppTemplateCatalog;
import com.salonplatform.whatsapp.WhatsAppTemplateDefinition;
import com.salonplatform.whatsapp.WhatsAppTemplateVariable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WhatsAppTemplateService {

    private final TenantWhatsAppTemplateSettingRepository settingRepository;
    private final BranchRepository branchRepository;

    @Transactional(readOnly = true)
    public List<WhatsAppTemplateResponse> list(UUID branchIdFilter, WhatsAppTemplateCategory categoryFilter, String search) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBrandAdminOrAbove();
        ensureDefaultSettings(tenantId);

        Map<WhatsAppTemplateCode, TenantWhatsAppTemplateSetting> settingsByCode = settingRepository.findByTenantId(tenantId)
                .stream()
                .filter(s -> s.getBranchId() == null)
                .collect(Collectors.toMap(TenantWhatsAppTemplateSetting::getTemplateCode, s -> s, (a, b) -> a));

        Map<UUID, String> branchNames = branchRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));

        String q = search != null ? search.trim().toLowerCase() : "";

        return WhatsAppTemplateCatalog.all().stream()
                .filter(def -> categoryFilter == null || def.getCategory() == categoryFilter)
                .filter(def -> branchIdFilter == null)
                .filter(def -> q.isEmpty()
                        || def.getDisplayName().toLowerCase().contains(q)
                        || def.getMsg91TemplateName().toLowerCase().contains(q)
                        || def.getDisplayBody().toLowerCase().contains(q)
                        || def.getTriggerDescription().toLowerCase().contains(q))
                .map(def -> toResponse(def, settingsByCode.get(def.getCode()), null, branchNames))
                .toList();
    }

    @Transactional
    public WhatsAppTemplateResponse updateSetting(
            WhatsAppTemplateCode code,
            UpdateWhatsAppTemplateSettingRequest request) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBrandAdminOrAbove();

        WhatsAppTemplateDefinition def = WhatsAppTemplateCatalog.find(code)
                .orElseThrow(() -> new ResourceNotFoundException("error.whatsappTemplate.notFound"));

        UUID branchId = request.getBranchId();
        TenantWhatsAppTemplateSetting setting = settingRepository
                .findByTenantIdAndTemplateCodeAndBranchId(tenantId, code, branchId)
                .orElseGet(() -> TenantWhatsAppTemplateSetting.builder()
                        .tenantId(tenantId)
                        .templateCode(code)
                        .branchId(branchId)
                        .active(def.isDefaultActive())
                        .build());

        setting.setActive(request.isActive());
        setting = settingRepository.save(setting);

        Map<UUID, String> branchNames = branchRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));

        return toResponse(def, setting, branchId, branchNames);
    }

    @Transactional(readOnly = true)
    public WhatsAppTemplatePreviewResponse preview(WhatsAppTemplateCode code, WhatsAppTemplatePreviewRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        WhatsAppTemplateDefinition def = WhatsAppTemplateCatalog.find(code)
                .orElseThrow(() -> new ResourceNotFoundException("error.whatsappTemplate.notFound"));

        Map<String, String> values = resolveVariableValues(def, request != null ? request.getVariableOverrides() : null);
        String body = renderBody(def.getBodyTemplate(), def.getVariables(), values);

        String headerPreview = null;
        if (def.isHasDocumentHeader()) {
            headerPreview = values.getOrDefault("invoiceNumber", "INV-0001") + ".pdf";
        }

        return WhatsAppTemplatePreviewResponse.builder()
                .code(def.getCode())
                .displayName(def.getDisplayName())
                .msg91TemplateName(def.getMsg91TemplateName())
                .category(def.getCategory())
                .hasDocumentHeader(def.isHasDocumentHeader())
                .headerPreview(headerPreview)
                .bodyText(body)
                .variables(def.getVariables().stream().map(this::toVariableResponse).toList())
                .metaNote("Template copy is Meta-approved on the Antrahq WhatsApp Business Account. "
                        + "Salons enable triggers here; changing wording requires a new Meta template submission.")
                .build();
    }

    public boolean isActive(UUID tenantId, UUID branchId, WhatsAppTemplateCode code) {
        ensureDefaultSettings(tenantId);
        Optional<TenantWhatsAppTemplateSetting> branchSetting = branchId != null
                ? settingRepository.findByTenantIdAndTemplateCodeAndBranchId(tenantId, code, branchId)
                : Optional.empty();
        if (branchSetting.isPresent()) {
            return branchSetting.get().isActive();
        }
        return settingRepository.findByTenantIdAndTemplateCodeAndBranchId(tenantId, code, null)
                .map(TenantWhatsAppTemplateSetting::isActive)
                .orElseGet(() -> WhatsAppTemplateCatalog.find(code).map(WhatsAppTemplateDefinition::isDefaultActive).orElse(false));
    }

    public String resolveTemplateName(WhatsAppTemplateCode code) {
        return WhatsAppTemplateCatalog.find(code)
                .map(WhatsAppTemplateDefinition::getMsg91TemplateName)
                .orElseThrow(() -> new ResourceNotFoundException("error.whatsappTemplate.notFound"));
    }

    public Optional<WhatsAppTemplateDefinition> findDefinition(WhatsAppTemplateCode code) {
        return WhatsAppTemplateCatalog.find(code);
    }

    @Transactional
    protected void ensureDefaultSettings(UUID tenantId) {
        List<TenantWhatsAppTemplateSetting> existing = settingRepository.findByTenantId(tenantId);
        Set<WhatsAppTemplateCode> seeded = existing.stream()
                .filter(s -> s.getBranchId() == null)
                .map(TenantWhatsAppTemplateSetting::getTemplateCode)
                .collect(Collectors.toSet());

        for (WhatsAppTemplateDefinition def : WhatsAppTemplateCatalog.all()) {
            if (!seeded.contains(def.getCode())) {
                settingRepository.save(TenantWhatsAppTemplateSetting.builder()
                        .tenantId(tenantId)
                        .templateCode(def.getCode())
                        .branchId(null)
                        .active(def.isDefaultActive())
                        .build());
            }
        }
    }

    public static String renderBody(
            String bodyTemplate,
            List<WhatsAppTemplateVariable> variables,
            Map<String, String> values) {
        String result = bodyTemplate != null ? bodyTemplate : "";
        for (WhatsAppTemplateVariable variable : variables) {
            String value = values.getOrDefault(variable.getKey(), variable.getSampleValue());
            if (value == null) {
                value = "";
            }
            result = result.replace("{{" + variable.getMetaIndex() + "}}", value);
        }
        return result.trim();
    }

    private Map<String, String> resolveVariableValues(
            WhatsAppTemplateDefinition def,
            Map<String, String> overrides) {
        Map<String, String> values = new LinkedHashMap<>();
        for (WhatsAppTemplateVariable variable : def.getVariables()) {
            values.put(variable.getKey(), variable.getSampleValue());
        }
        if (overrides != null) {
            overrides.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    values.put(key, value.trim());
                }
            });
        }
        return values;
    }

    private WhatsAppTemplateResponse toResponse(
            WhatsAppTemplateDefinition def,
            TenantWhatsAppTemplateSetting setting,
            UUID branchId,
            Map<UUID, String> branchNames) {
        boolean active = setting != null ? setting.isActive() : def.isDefaultActive();
        return WhatsAppTemplateResponse.builder()
                .code(def.getCode())
                .displayName(def.getDisplayName())
                .msg91TemplateName(def.getMsg91TemplateName())
                .category(def.getCategory())
                .triggerDescription(def.getTriggerDescription())
                .displayBody(def.getDisplayBody())
                .wired(def.isWired())
                .active(active)
                .branchId(branchId)
                .branchName(branchId != null ? branchNames.getOrDefault(branchId, "—") : null)
                .hasDocumentHeader(def.isHasDocumentHeader())
                .variables(def.getVariables().stream().map(this::toVariableResponse).toList())
                .build();
    }

    private WhatsAppTemplateVariableResponse toVariableResponse(WhatsAppTemplateVariable variable) {
        return WhatsAppTemplateVariableResponse.builder()
                .key(variable.getKey())
                .label(variable.getLabel())
                .metaIndex(variable.getMetaIndex())
                .sampleValue(variable.getSampleValue())
                .build();
    }
}
