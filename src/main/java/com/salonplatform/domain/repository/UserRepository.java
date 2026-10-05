package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    /** Login fallback for accounts whose stored email has capitals (login input is lower-cased). */
    Optional<User> findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(String email);
    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);
    List<User> findByTenantId(UUID tenantId);
    List<User> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
    List<User> findByRoleAndActiveTrue(UserRole role);

    List<User> findByRoleOrderByActiveDescNameAsc(UserRole role);
}
