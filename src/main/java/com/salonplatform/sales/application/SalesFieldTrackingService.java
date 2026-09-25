package com.salonplatform.sales.application;

import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.sales.domain.entity.SalesFieldLocationPing;
import com.salonplatform.sales.domain.repository.SalesFieldLocationPingRepository;
import com.salonplatform.sales.dto.ActiveFieldRepResponse;
import com.salonplatform.sales.dto.CreateFieldLocationPingRequest;
import com.salonplatform.sales.dto.FieldLocationPingResponse;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesFieldTrackingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    /**
     * Reps ping about every 5 minutes in field mode — treat as active if the latest ping is within this window.
     */
    private static final Duration ACTIVE_WINDOW = Duration.ofMinutes(6);

    private static final Duration LATEST_PING_LOOKBACK = Duration.ofDays(365);

    private final SalesFieldLocationPingRepository pingRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recordPing(CreateFieldLocationPingRequest request) {
        SecurityUtils.assertSalesAccess();
        if (request.getLatitude() < -90 || request.getLatitude() > 90
                || request.getLongitude() < -180 || request.getLongitude() > 180) {
            throw new BadRequestException("Invalid coordinates");
        }
        pingRepository.save(SalesFieldLocationPing.builder()
                .repId(SecurityUtils.currentUserId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracyMeters(request.getAccuracyMeters())
                .capturedAt(request.getCapturedAt())
                .createdAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public List<ActiveFieldRepResponse> listActiveReps() {
        SecurityUtils.assertPlatformAdmin();
        Map<UUID, SalesFieldLocationPing> latestByRep = latestPingByRep();
        Instant now = Instant.now();
        long activeThresholdSeconds = ACTIVE_WINDOW.getSeconds();

        return userRepository.findByRoleAndActiveTrue(UserRole.SALES_EXECUTIVE).stream()
                .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                .map(rep -> toFieldRepResponse(rep, latestByRep.get(rep.getId()), now, activeThresholdSeconds))
                .toList();
    }

    private Map<UUID, SalesFieldLocationPing> latestPingByRep() {
        Instant since = Instant.now().minus(LATEST_PING_LOOKBACK);
        List<SalesFieldLocationPing> recent = pingRepository.findByCapturedAtAfterOrderByCapturedAtDesc(since);
        Map<UUID, SalesFieldLocationPing> latestByRep = new LinkedHashMap<>();
        for (SalesFieldLocationPing ping : recent) {
            latestByRep.putIfAbsent(ping.getRepId(), ping);
        }
        return latestByRep;
    }

    private ActiveFieldRepResponse toFieldRepResponse(
            User rep,
            SalesFieldLocationPing ping,
            Instant now,
            long activeThresholdSeconds) {
        if (ping == null) {
            return ActiveFieldRepResponse.builder()
                    .repId(rep.getId())
                    .repName(rep.getName())
                    .active(false)
                    .hasLocation(false)
                    .build();
        }
        long secondsSince = Duration.between(ping.getCapturedAt(), now).getSeconds();
        return ActiveFieldRepResponse.builder()
                .repId(rep.getId())
                .repName(rep.getName())
                .active(secondsSince < activeThresholdSeconds)
                .hasLocation(true)
                .latitude(ping.getLatitude())
                .longitude(ping.getLongitude())
                .accuracyMeters(ping.getAccuracyMeters())
                .capturedAt(ping.getCapturedAt())
                .secondsSinceLastPing(secondsSince)
                .build();
    }

    @Transactional(readOnly = true)
    public List<FieldLocationPingResponse> getTrail(UUID repId, LocalDate date) {
        SecurityUtils.assertPlatformAdmin();
        LocalDate day = date != null ? date : LocalDate.now(ZONE);
        Instant start = day.atStartOfDay(ZONE).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(ZONE).toInstant();
        return pingRepository.findByRepIdAndCapturedAtBetweenOrderByCapturedAtAsc(repId, start, end).stream()
                .map(ping -> FieldLocationPingResponse.builder()
                        .latitude(ping.getLatitude())
                        .longitude(ping.getLongitude())
                        .accuracyMeters(ping.getAccuracyMeters())
                        .capturedAt(ping.getCapturedAt())
                        .build())
                .toList();
    }
}
