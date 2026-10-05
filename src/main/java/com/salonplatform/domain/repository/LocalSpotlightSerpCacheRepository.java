package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.LocalSpotlightSerpCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface LocalSpotlightSerpCacheRepository extends JpaRepository<LocalSpotlightSerpCache, UUID> {

    Optional<LocalSpotlightSerpCache> findByCacheKeyAndSnapshotDate(String cacheKey, LocalDate snapshotDate);
}
