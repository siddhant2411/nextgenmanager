package com.nextgenmanager.nextgenmanager.common.audit;

import lombok.Getter;

/**
 * One event to write to the audit trail, built fluently:
 * {@code AuditEvent.of(UPDATE, LEDGER, id).label("4011 Bank").summary("Opening balance changed").details(diff)}.
 * The user defaults to whoever is signed in; {@link #by(String)} names someone else (an event that
 * carried its user across a transaction boundary).
 */
@Getter
public class AuditEvent {
    private final String action;
    private final String entityType;
    private final Long entityId;
    private String label;
    private String summary;
    private Object details;
    private Long voucherId;
    private String username;
    private boolean userGiven;

    private AuditEvent(String action, String entityType, Long entityId) {
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public static AuditEvent of(String action, String entityType, Long entityId) {
        return new AuditEvent(action, entityType, entityId);
    }

    public AuditEvent label(String label) { this.label = label; return this; }
    public AuditEvent summary(String summary) { this.summary = summary; return this; }
    public AuditEvent details(Object details) { this.details = details; return this; }
    public AuditEvent voucher(Long voucherId) { this.voucherId = voucherId; return this; }
    public AuditEvent by(String username) { this.username = username; this.userGiven = true; return this; }
}
