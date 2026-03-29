package com.nextgenmanager.nextgenmanager.bom.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BomAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer bomId;

    @Enumerated(EnumType.STRING)
    private BomStatus oldStatus;

    @Enumerated(EnumType.STRING)
    private BomStatus newStatus;

    private Instant changedAt;

    private String changedBy;     // user or SYSTEM

    private String source;        // UI, API, SYSTEM, WORKFLOW

    @Column(length = 2000)
    private String comment;       // rejection reason, notes, etc.

    @Column(length = 4000)
    private String payloadJson;   // optional, for full audit snapshots
}
