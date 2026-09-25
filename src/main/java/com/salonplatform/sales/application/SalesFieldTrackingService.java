package com.salonplatform.sales.application;

import com.salonplatform.domain.entity.User;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesFieldTrackingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    /** A rep with no ping in this long is treated as no longer in field mode. */
    private static final Duration ACTIVE_WINDOW = Duration.ofMinutes(3);

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
        Instant since = Instant.now().minus(ACTIVE_WINDOW);
        List<SalesFieldLocationPing> recent = pingRepository.findByCapturedAtAfterOrderByCapturedAtDesc(since);

        Map<UUID, SalesFieldLocationPing> latestByRep = new LinkedHashMap<>();
        for (SalesFieldLocationPing ping : recent) {
            latestByRep.putIfAbsent(ping.getRepId(), ping);
        }

        Instant now = Instant.now();
        return latestByRep.values().stream()
                .map(ping -> ActiveFieldRepResponse.builder()
                        .repId(ping.getRepId())
                        .repName(userRepository.findById(ping.getRepId()).map(User::getName).orElse("Unknown"))
                        .latitude(ping.getLatitude())
                        .longitude(ping.getLongitude())
                        .accuracyMeters(ping.getAccuracyMeters())
                        .capturedAt(ping.getCapturedAt())
                        .secondsSinceLastPing(Duration.between(ping.getCapturedAt(), now).getSeconds())
                        .build())
                .toList();
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
