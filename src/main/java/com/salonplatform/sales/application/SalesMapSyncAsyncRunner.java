package com.salonplatform.sales.application;

import com.salonplatform.sales.domain.entity.SalesMapSyncJob;
import com.salonplatform.sales.domain.enums.MapSyncStatus;
import com.salonplatform.sales.domain.repository.SalesMapSyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SalesMapSyncAsyncRunner {

    private final SalesMapSyncJobRepository jobRepository;
    private final SalesLeadDiscoveryService leadDiscoveryService;

    @Async
    public void runJob(UUID jobId) {
        SalesMapSyncJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Map sync job {} not found (may have been deleted)", jobId);
            return;
        }
        job.setStatus(MapSyncStatus.RUNNING);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);
        try {
            leadDiscoveryService.executeMapSyncJob(jobId);
            job = jobRepository.findById(jobId).orElse(job);
            job.setStatus(MapSyncStatus.COMPLETED);
            job.setFinishedAt(Instant.now());
            job.setCurrentAreaName(null);
            jobRepository.save(job);
        } catch (Exception e) {
            log.error("Map sync job {} failed: {}", jobId, e.getMessage(), e);
            job = jobRepository.findById(jobId).orElse(job);
            job.setStatus(MapSyncStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
        }
    }
}
