package com.salonplatform.sales.domain.repository;

import com.salonplatform.sales.domain.entity.SalesMapSyncJob;
import com.salonplatform.sales.domain.enums.MapSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SalesMapSyncJobRepository extends JpaRepository<SalesMapSyncJob, UUID> {

    Optional<SalesMapSyncJob> findFirstByStatusInOrderByCreatedAtDesc(Collection<MapSyncStatus> statuses);
}
