package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.WhatsAppTemplateCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "tenant_whatsapp_template_settings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "template_code", "branch_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantWhatsAppTemplateSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private WhatsAppTemplateCode templateCode;

    /** Null = tenant-wide default for all branches. */
    private UUID branchId;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
