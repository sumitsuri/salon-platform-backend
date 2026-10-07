package com.salonplatform.service;

import com.salonplatform.dto.analytics.LocalSpotlightSyncProgressResponse;
import com.salonplatform.dto.analytics.LocalSpotlightSyncResponse;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocalSpotlightSyncProgressService {

    private static final Duration LAST_OUTCOME_TTL = Duration.ofMinutes(15);

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
    private final Map<UUID, LastOutcome> lastOutcomeByTenant = new ConcurrentHashMap<>();

    @Data
    @Builder
    private static final class LastOutcome {
        private String message;
        private String error;
        private Boolean skipped;
        private Instant finishedAt;
    }

    /** Reserves the tenant sync slot before background work starts (prevents double POST). */
    public boolean tryAcquire(UUID tenantId) {
        State placeholder = State.builder()
                .phase(Phase.STARTING)
                .completedSteps(0)
                .totalSteps(1)
                .startedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return byTenant.putIfAbsent(tenantId, placeholder) == null;
    }

    public void start(UUID tenantId, int keywordCount) {
        lastOutcomeByTenant.remove(tenantId);
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

    public void recordLastOutcome(UUID tenantId, LocalSpotlightSyncResponse response, String error) {
        if (error != null && !error.isBlank()) {
            lastOutcomeByTenant.put(
                    tenantId,
                    LastOutcome.builder().error(error).finishedAt(Instant.now()).build());
            return;
        }
        if (response == null) {
            return;
        }
        lastOutcomeByTenant.put(
                tenantId,
                LastOutcome.builder()
                        .message(response.getMessage())
                        .skipped(response.isSkipped())
                        .finishedAt(Instant.now())
                        .build());
    }

    public boolean isActive(UUID tenantId) {
        return byTenant.containsKey(tenantId);
    }

    public Optional<LocalSpotlightSyncProgressResponse> snapshot(UUID tenantId) {
        State state = byTenant.get(tenantId);
        if (state == null) {
            LocalSpotlightSyncProgressResponse.LocalSpotlightSyncProgressResponseBuilder idle =
                    LocalSpotlightSyncProgressResponse.builder()
                            .active(false)
                            .phase(Phase.IDLE.name())
                            .completedSteps(0)
                            .totalSteps(0)
                            .percent(0);
            applyRecentLastOutcome(tenantId, idle);
            return Optional.of(idle.build());
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

    private void applyRecentLastOutcome(
            UUID tenantId, LocalSpotlightSyncProgressResponse.LocalSpotlightSyncProgressResponseBuilder builder) {
        LastOutcome last = lastOutcomeByTenant.get(tenantId);
        if (last == null || last.getFinishedAt() == null) {
            return;
        }
        if (Duration.between(last.getFinishedAt(), Instant.now()).compareTo(LAST_OUTCOME_TTL) > 0) {
            lastOutcomeByTenant.remove(tenantId);
            return;
        }
        builder.lastSyncMessage(last.getMessage());
        builder.lastSyncError(last.getError());
        builder.lastSyncSkipped(last.getSkipped());
    }
}
