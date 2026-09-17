package com.salonplatform.domain.entity;

import com.salonplatform.domain.enums.DiscountType;
import com.salonplatform.domain.enums.ScratchPrizeKind;
import com.salonplatform.domain.enums.ServiceScopeType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "scratch_campaign_prizes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScratchCampaignPrize {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private ScratchCampaign campaign;

    @Column(nullable = false, length = 160)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScratchPrizeKind prizeKind;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DiscountType discountType;

    @Column(precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ServiceScopeType serviceScope;

    @Column(length = 2000)
    private String scopeIds;

    @Builder.Default
    private Integer weight = 1;

    @Builder.Default
    private Integer sortOrder = 0;
}
