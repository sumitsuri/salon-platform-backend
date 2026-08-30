package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.TenantWhatsAppTemplateSetting;
import com.salonplatform.domain.enums.WhatsAppTemplateCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantWhatsAppTemplateSettingRepository extends JpaRepository<TenantWhatsAppTemplateSetting, UUID> {

    List<TenantWhatsAppTemplateSetting> findByTenantId(UUID tenantId);

    Optional<TenantWhatsAppTemplateSetting> findByTenantIdAndTemplateCodeAndBranchId(
            UUID tenantId, WhatsAppTemplateCode templateCode, UUID branchId);
}
