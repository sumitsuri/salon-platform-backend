package com.salonplatform.sales.domain.repository;

import com.salonplatform.sales.domain.entity.SalesTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesTargetRepository extends JpaRepository<SalesTarget, UUID> {
    Optional<SalesTarget> findByRepIdAndWeekStartDate(UUID repId, LocalDate weekStartDate);

    List<SalesTarget> findByWeekStartDate(LocalDate weekStartDate);

    List<SalesTarget> findByRepId(UUID repId);
}
