package com.salonplatform.sales.domain.repository;

import com.salonplatform.sales.domain.entity.SalesFieldLocationPing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SalesFieldLocationPingRepository extends JpaRepository<SalesFieldLocationPing, UUID> {

    /** Most recent-first pings since a cutoff, across all reps — used to derive who's currently active. */
    List<SalesFieldLocationPing> findByCapturedAtAfterOrderByCapturedAtDesc(Instant since);

    List<SalesFieldLocationPing> findByRepIdAndCapturedAtBetweenOrderByCapturedAtAsc(
            UUID repId, Instant start, Instant end);
}
