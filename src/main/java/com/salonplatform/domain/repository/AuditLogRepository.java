package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    boolean existsByAction(String action);
}
