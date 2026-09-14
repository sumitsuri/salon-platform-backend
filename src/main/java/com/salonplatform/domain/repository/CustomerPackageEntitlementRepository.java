package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.CustomerPackageEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPackageEntitlementRepository extends JpaRepository<CustomerPackageEntitlement, UUID> {

    List<CustomerPackageEntitlement> findBySubscriptionIdOrderByServiceNameAsc(UUID subscriptionId);

    Optional<CustomerPackageEntitlement> findBySubscriptionIdAndServiceId(UUID subscriptionId, UUID serviceId);
}
