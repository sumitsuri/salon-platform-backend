package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.FaceScanCapture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FaceScanCaptureRepository extends JpaRepository<FaceScanCapture, UUID> {

    List<FaceScanCapture> findBySessionIdOrderByCapturedAtAsc(UUID sessionId);

    long countBySessionId(UUID sessionId);
}
