package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);
    List<User> findByTenantId(UUID tenantId);
    List<User> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
}
