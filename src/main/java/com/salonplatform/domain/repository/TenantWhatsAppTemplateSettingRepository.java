package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.TenantWhatsAppTemplateSetting;
import com.salonplatform.domain.enums.WhatsAppTemplateCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantWhatsAppTemplateSettingRepository extends JpaRepository<TenantWhatsAppTemplateSetting, UUID> {

    List<TenantWhatsAppTemplateSetting> findByTenantId(UUID tenantId);

    // findFirst: branch_id NULL rows are not covered by the unique constraint in Postgres, so duplicates can exist.
    Optional<TenantWhatsAppTemplateSetting> findFirstByTenantIdAndTemplateCodeAndBranchIdOrderByUpdatedAtDesc(
            UUID tenantId, WhatsAppTemplateCode templateCode, UUID branchId);
}
