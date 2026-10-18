package com.nextgenmanager.nextgenmanager.common.audit.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.util.Date;

/**
 * One change to the books or the masters behind them: who made it, when, and what changed. Written
 * once and never touched again — the entity is immutable and the table rejects updates and deletes.
 */
@Entity
@Immutable
@Table(name = "audittrail")
@Getter @Setter @NoArgsConstructor
public class AuditTrailEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Date eventTime;

    @Column(length = 100)
    private String username;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(nullable = false, length = 40)
    private String entityType;

    private Long entityId;

    @Column(length = 200)
    private String entityLabel;

    @Column(length = 500)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String details;

    private Long voucherId;
}
