package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.ScalpScanCapture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScalpScanCaptureRepository extends JpaRepository<ScalpScanCapture, UUID> {

    List<ScalpScanCapture> findBySessionIdOrderByCapturedAtAsc(UUID sessionId);

    long countBySessionId(UUID sessionId);
}
