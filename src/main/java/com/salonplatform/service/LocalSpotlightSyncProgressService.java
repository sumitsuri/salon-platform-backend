package com.salonplatform.service;

import com.salonplatform.dto.analytics.LocalSpotlightSyncProgressResponse;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocalSpotlightSyncProgressService {

    public enum Phase {
        STARTING,
        LISTING,
        RIVALS,
        KEYWORDS,
        FINISHING,
        IDLE
    }

    @Data
    @Builder
    private static final class State {
        private Phase phase;
        private int completedSteps;
        private int totalSteps;
        private String detail;
        private Instant startedAt;
        private Instant updatedAt;
    }

    private final Map<UUID, State> byTenant = new ConcurrentHashMap<>();

    public void start(UUID tenantId, int keywordCount) {
        int keywords = Math.max(0, keywordCount);
        int total = 2 + keywords;
        byTenant.put(
                tenantId,
                State.builder()
                        .phase(Phase.STARTING)
                        .completedSteps(0)
                        .totalSteps(Math.max(total, 1))
                        .startedAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());
    }

    public void setPhase(UUID tenantId, Phase phase, int completedSteps, String detail) {
        State state = byTenant.get(tenantId);
        if (state == null) {
            return;
        }
        state.setPhase(phase);
        state.setCompletedSteps(Math.min(completedSteps, state.getTotalSteps()));
        if (detail != null) {
            state.setDetail(detail);
        }
        state.setUpdatedAt(Instant.now());
    }

    public void keywordStep(UUID tenantId, int keywordIndexOneBased, int keywordTotal, String keyword) {
        State state = byTenant.get(tenantId);
        if (state == null) {
            return;
        }
        int base = 2;
        int completed = base + Math.min(keywordIndexOneBased, keywordTotal);
        state.setPhase(Phase.KEYWORDS);
        state.setCompletedSteps(Math.min(completed, state.getTotalSteps()));
        state.setDetail(keyword);
        state.setUpdatedAt(Instant.now());
    }

    public void finish(UUID tenantId) {
        State state = byTenant.get(tenantId);
        if (state == null) {
            return;
        }
        state.setPhase(Phase.FINISHING);
        state.setCompletedSteps(state.getTotalSteps());
        state.setUpdatedAt(Instant.now());
        byTenant.remove(tenantId);
    }

    public void clear(UUID tenantId) {
        byTenant.remove(tenantId);
    }

    public boolean isActive(UUID tenantId) {
        return byTenant.containsKey(tenantId);
    }

    public Optional<LocalSpotlightSyncProgressResponse> snapshot(UUID tenantId) {
        State state = byTenant.get(tenantId);
        if (state == null) {
            return Optional.of(LocalSpotlightSyncProgressResponse.builder()
                    .active(false)
                    .phase(Phase.IDLE.name())
                    .completedSteps(0)
                    .totalSteps(0)
                    .percent(0)
                    .build());
        }
        int total = Math.max(state.getTotalSteps(), 1);
        int completed = Math.min(state.getCompletedSteps(), total);
        int percent = (completed * 100) / total;
        if (state.getPhase() == Phase.FINISHING) {
            percent = 100;
        }
        return Optional.of(LocalSpotlightSyncProgressResponse.builder()
                .active(true)
                .phase(state.getPhase().name())
                .completedSteps(completed)
                .totalSteps(total)
                .detail(state.getDetail())
                .percent(percent)
                .build());
    }
}
