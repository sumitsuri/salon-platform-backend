package com.salonplatform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "invoice_sequences", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "fiscal_year"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID branchId;

    @Column(nullable = false, length = 10)
    private String fiscalYear;

    @Builder.Default
    private Long lastSequence = 0L;
}
