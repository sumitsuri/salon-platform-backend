package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.MembershipSubscription;
import com.salonplatform.domain.enums.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipSubscriptionRepository extends JpaRepository<MembershipSubscription, UUID> {
    List<MembershipSubscription> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID tenantId, UUID customerId);

    Optional<MembershipSubscription> findFirstByTenantIdAndCustomerIdAndStatusAndEndsOnGreaterThanEqualOrderByEndsOnDesc(
            UUID tenantId, UUID customerId, MembershipStatus status, LocalDate onOrAfter);

    List<MembershipSubscription> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<MembershipSubscription> findByTenantIdAndStatusAndEndsOnGreaterThanEqualOrderByEndsOnAsc(
            UUID tenantId, MembershipStatus status, LocalDate onOrAfter);

    List<MembershipSubscription> findByTenantIdAndBranchIdAndStatusAndEndsOnGreaterThanEqualOrderByEndsOnAsc(
            UUID tenantId, UUID branchId, MembershipStatus status, LocalDate onOrAfter);

    List<MembershipSubscription> findByTenantIdAndStatusAndEndsOnBefore(
            UUID tenantId, MembershipStatus status, LocalDate before);
}
