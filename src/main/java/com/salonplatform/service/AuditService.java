package com.salonplatform.service;

import com.salonplatform.domain.entity.AuditLog;
import com.salonplatform.domain.repository.AuditLogRepository;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(String action, String entityType, UUID entityId, String details) {
        UserPrincipal user = SecurityUtils.currentUser();
        auditLogRepository.save(AuditLog.builder()
                .tenantId(user.getTenantId())
                .branchId(user.getBranchId())
                .userId(user.getId())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .build());
    }
}
