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

    Optional<Customer> findByBranchIdAndPhone(UUID branchId, String phone);

    Optional<Customer> findByBranchIdAndVisitPassId(UUID branchId, String visitPassId);

    Optional<Customer> findByPassPublicToken(String passPublicToken);

    @Query("SELECT c FROM Customer c WHERE c.branchId = :branchId AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) OR c.phone LIKE CONCAT('%', :q, '%') " +
           "OR UPPER(c.visitPassId) LIKE UPPER(CONCAT('%', :q, '%')))")
    List<Customer> searchByBranch(@Param("branchId") UUID branchId, @Param("q") String query);
}
