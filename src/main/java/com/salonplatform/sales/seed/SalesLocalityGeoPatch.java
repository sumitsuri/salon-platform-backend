package com.salonplatform.sales.seed;

import com.salonplatform.sales.domain.BangaloreSalesAreas;
import com.salonplatform.sales.domain.entity.SalesLocality;
import com.salonplatform.sales.domain.repository.SalesLocalityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Ensures Bangalore sales areas exist with map coordinates for lead discovery. */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class SalesLocalityGeoPatch implements CommandLineRunner {

    private final SalesLocalityRepository localityRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, SalesLocality> byName = localityRepository.findAll().stream()
                .collect(Collectors.toMap(
                        l -> l.getName().trim().toLowerCase(Locale.ROOT),
                        l -> l,
                        (a, b) -> a));

        int upserted = 0;
        for (BangaloreSalesAreas.Area area : BangaloreSalesAreas.ALL) {
            String key = area.name().trim().toLowerCase(Locale.ROOT);
            SalesLocality loc = byName.get(key);
            if (loc == null) {
                loc = SalesLocality.builder()
                        .name(area.name())
                        .zone(area.zone())
                        .latitude(area.latitude())
                        .longitude(area.longitude())
                        .active(true)
                        .build();
                localityRepository.save(loc);
                upserted++;
            } else {
                boolean changed = false;
                if (loc.getLatitude() == null || loc.getLongitude() == null) {
                    loc.setLatitude(area.latitude());
                    loc.setLongitude(area.longitude());
                    changed = true;
                }
                if (loc.getZone() == null || loc.getZone().isBlank()) {
                    loc.setZone(area.zone());
                    changed = true;
                }
                if (changed) {
                    localityRepository.save(loc);
                    upserted++;
                }
            }
        }
        if (upserted > 0) {
            log.info("Sales locality geo patch: updated or added {} Bangalore areas", upserted);
        }
    }
}
