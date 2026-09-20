package com.salonplatform.sales.application;

import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.sales.config.SalesMapIngestionProperties;
import com.salonplatform.sales.domain.entity.SalesLocality;
import com.salonplatform.sales.domain.entity.SalesMapSyncJob;
import com.salonplatform.sales.domain.enums.MapSyncStatus;
import com.salonplatform.sales.domain.repository.SalesLocalityRepository;
import com.salonplatform.sales.domain.repository.SalesMapSyncJobRepository;
import com.salonplatform.sales.dto.MapSyncJobResponse;
import com.salonplatform.sales.dto.StartMapSyncRequest;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesMapSyncService {

    private final SalesMapSyncJobRepository jobRepository;
    private final SalesLocalityRepository localityRepository;
    private final SalesMapIngestionProperties ingestionProperties;
    private final SalesMapSyncAsyncRunner asyncRunner;

    /** Marks in-flight jobs failed after process restart so a new sync can start. */
    @Transactional
    public void failInterruptedJobs() {
        jobRepository
                .findFirstByStatusInOrderByCreatedAtDesc(EnumSet.of(MapSyncStatus.QUEUED, MapSyncStatus.RUNNING))
                .ifPresent(job -> {
                    job.setStatus(MapSyncStatus.FAILED);
                    job.setErrorMessage("Interrupted by server restart");
                    job.setFinishedAt(Instant.now());
                    jobRepository.save(job);
                });
    }

    @Transactional
    public MapSyncJobResponse startScheduledAllAreasSync() {
        StartMapSyncRequest request = new StartMapSyncRequest();
        request.setAllAreas(true);
        request.setRadiusKm(ingestionProperties.getRadiusKm());
        return startSyncInternal(request, null);
    }

    @Transactional
    public MapSyncJobResponse startSync(StartMapSyncRequest request) {
        SecurityUtils.assertSalesAccess();
        return startSyncInternal(request, SecurityUtils.currentUserId());
    }

    private MapSyncJobResponse startSyncInternal(StartMapSyncRequest request, UUID triggeredBy) {

        var active = jobRepository.findFirstByStatusInOrderByCreatedAtDesc(
                EnumSet.of(MapSyncStatus.QUEUED, MapSyncStatus.RUNNING));
        if (active.isPresent()) {
            SalesMapSyncJob running = active.get();
            if (running.getStatus() == MapSyncStatus.RUNNING
                    && running.getStartedAt() != null
                    && running.getStartedAt().isBefore(java.time.Instant.now().minusSeconds(7200))) {
                running.setStatus(MapSyncStatus.FAILED);
                running.setErrorMessage("Sync timed out (stale job cleared)");
                running.setFinishedAt(java.time.Instant.now());
                jobRepository.save(running);
            } else {
                return toResponse(running);
            }
        }

        int radiusKm = request.getRadiusKm() != null
                ? clampRadius(request.getRadiusKm())
                : ingestionProperties.getRadiusKm();

        SalesMapSyncJob job = SalesMapSyncJob.builder()
                .status(MapSyncStatus.QUEUED)
                .radiusKm(radiusKm)
                .triggeredByUserId(triggeredBy)
                .build();

        if (request.isAllAreas()) {
            List<SalesLocality> areas = mappableLocalities();
            job.setTotalAreas(areas.size());
            job.setLocalityName("All Bangalore areas");
        } else {
            if (request.getLocalityId() == null) {
                throw new BadRequestException("localityId is required when allAreas is false");
            }
            SalesLocality area = localityRepository.findById(request.getLocalityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Area not found"));
            job.setLocalityId(area.getId());
            job.setLocalityName(area.getName());
            job.setTotalAreas(1);
        }

        job = jobRepository.save(job);
        UUID jobId = job.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncRunner.runJob(jobId);
                }
            });
        } else {
            asyncRunner.runJob(jobId);
        }
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public MapSyncJobResponse getJob(UUID jobId) {
        SecurityUtils.assertSalesAccess();
        return jobRepository.findById(jobId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Sync job not found"));
    }

    @Transactional(readOnly = true)
    public MapSyncJobResponse getActiveJob() {
        SecurityUtils.assertSalesAccess();
        return jobRepository
                .findFirstByStatusInOrderByCreatedAtDesc(EnumSet.of(MapSyncStatus.QUEUED, MapSyncStatus.RUNNING))
                .map(this::toResponse)
                .orElse(null);
    }

    private List<SalesLocality> mappableLocalities() {
        return localityRepository.findByActiveTrueOrderByZoneAscNameAsc().stream()
                .filter(l -> l.getLatitude() != null && l.getLongitude() != null)
                .toList();
    }

    private static int clampRadius(int radiusKm) {
        return Math.max(1, Math.min(radiusKm, 15));
    }

    MapSyncJobResponse toResponse(SalesMapSyncJob job) {
        int progress = 0;
        if (job.getTotalAreas() > 0) {
            progress = (int) Math.round(100.0 * job.getCompletedAreas() / job.getTotalAreas());
        } else if (job.getStatus() == MapSyncStatus.COMPLETED) {
            progress = 100;
        }
        return MapSyncJobResponse.builder()
                .id(job.getId())
                .status(job.getStatus())
                .allAreas(job.getLocalityId() == null && job.getTotalAreas() > 1)
                .localityId(job.getLocalityId())
                .localityName(job.getLocalityName())
                .radiusKm(job.getRadiusKm())
                .totalAreas(job.getTotalAreas())
                .completedAreas(job.getCompletedAreas())
                .currentAreaName(job.getCurrentAreaName())
                .leadsInserted(job.getLeadsInserted())
                .leadsSkippedDuplicate(job.getLeadsSkippedDuplicate())
                .areaErrors(job.getAreaErrors())
                .errorMessage(job.getErrorMessage())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .progressPercent(Math.min(100, progress))
                .build();
    }
}
