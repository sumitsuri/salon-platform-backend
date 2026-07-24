package com.salonplatform.config;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class BranchGeofencePatch implements CommandLineRunner {

    private final BranchRepository branchRepository;

    @Override
    @Transactional
    public void run(String... args) {
        branchRepository.findAll().stream()
                .filter(b -> b.getLatitude() == null && "LIT".equals(b.getCode()))
                .forEach(b -> {
                    b.setLatitude(12.9352);
                    b.setLongitude(77.6245);
                    b.setGeofenceRadiusMeters(150);
                    b.setAttendanceGraceMinutes(15);
                    branchRepository.save(b);
                });
        branchRepository.findAll().stream()
                .filter(b -> b.getLatitude() == null && "WEB".equals(b.getCode()))
                .forEach(b -> {
                    b.setLatitude(12.9716);
                    b.setLongitude(77.5946);
                    b.setGeofenceRadiusMeters(150);
                    b.setAttendanceGraceMinutes(15);
                    branchRepository.save(b);
                });
    }
}
