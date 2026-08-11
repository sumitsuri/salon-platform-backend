package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findByTenantIdAndPhone(UUID tenantId, String phone);

    Optional<Customer> findByTenantIdAndVisitPassId(UUID tenantId, String visitPassId);

    Optional<Customer> findByPassPublicToken(String passPublicToken);

    @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) OR c.phone LIKE CONCAT('%', :q, '%') " +
           "OR UPPER(c.visitPassId) LIKE UPPER(CONCAT('%', :q, '%')))")
    List<Customer> search(@Param("tenantId") UUID tenantId, @Param("q") String query);
}
