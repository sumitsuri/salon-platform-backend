package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.MessageDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MessageDeliveryLogRepository extends JpaRepository<MessageDeliveryLog, UUID> {
}
