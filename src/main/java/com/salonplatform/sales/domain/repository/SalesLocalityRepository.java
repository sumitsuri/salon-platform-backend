package com.salonplatform.sales.domain.repository;

import com.salonplatform.sales.domain.entity.SalesLocality;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesLocalityRepository extends JpaRepository<SalesLocality, UUID> {
    List<SalesLocality> findByActiveTrueOrderByZoneAscNameAsc();
}
