package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Coupon;
import com.salonplatform.domain.enums.PromoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    List<Coupon> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Coupon> findByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    List<Coupon> findByTenantIdAndStatus(UUID tenantId, PromoStatus status);
}
