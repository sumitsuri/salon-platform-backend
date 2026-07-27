package com.salonplatform.sales.domain.repository;

import com.salonplatform.sales.domain.entity.SalesLead;
import com.salonplatform.sales.domain.enums.LeadStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SalesLeadRepository extends JpaRepository<SalesLead, UUID>, JpaSpecificationExecutor<SalesLead> {

    List<SalesLead> findByAssignedRepId(UUID assignedRepId);

    long countByAssignedRepIdAndStageNot(UUID assignedRepId, LeadStage stage);

    @Query("SELECT l.stage, COUNT(l) FROM SalesLead l GROUP BY l.stage")
    List<Object[]> countByStage();

    long countByStage(LeadStage stage);

    long countByAssignedRepIdAndStage(UUID assignedRepId, LeadStage stage);
}
