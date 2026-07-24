package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.MessageChannel;
import com.salonplatform.domain.enums.MessageDeliveryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_delivery_logs", indexes = {
        @Index(columnList = "tenant_id, created_at"),
        @Index(columnList = "campaign_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    private UUID campaignId;
    private UUID customerId;
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageChannel channel;

    @Column(nullable = false, length = 20)
    private String recipientPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageDeliveryStatus status;

    private String providerMessageId;
    private String errorMessage;

    @CreationTimestamp
    private Instant createdAt;
}
