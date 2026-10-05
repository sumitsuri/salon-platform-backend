package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.LocalSpotlightKeywordRankDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocalSpotlightKeywordRankDailyRepository extends JpaRepository<LocalSpotlightKeywordRankDaily, UUID> {

    Optional<LocalSpotlightKeywordRankDaily> findByBranchIdAndKeywordAndSnapshotDate(
            UUID branchId, String keyword, LocalDate snapshotDate);

    List<LocalSpotlightKeywordRankDaily> findByBranchIdAndSnapshotDateBetweenOrderByKeywordAscSnapshotDateAsc(
            UUID branchId, LocalDate from, LocalDate to);

    List<LocalSpotlightKeywordRankDaily> findByBranchIdAndKeywordAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            UUID branchId, String keyword, LocalDate from, LocalDate to);
}
