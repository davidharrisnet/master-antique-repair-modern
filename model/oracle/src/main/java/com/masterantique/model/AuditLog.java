package com.masterantique.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Workflow audit trail in the legacy format: timestamp, actor, action code, entity type code, entity id. It holds ids
 * and timestamps only, never comment text or descriptions (there is no column for them). Rows are only ever added.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_id", nullable = false)
    private Integer userId;                   // the actor

    @Enumerated(EnumType.ORDINAL)             // NUMBER(10), legacy ActionType code 0-11
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.ORDINAL)             // NUMBER(10), legacy EntityKind code 0-2
    @Column(name = "entity_type", nullable = false)
    private AuditEntityKind entityType;

    @Column(name = "entity_id", nullable = false)
    private Integer entityId;

    protected AuditLog() {
    }

    /** A new audit row; all values are required ({@link IllegalArgumentException} otherwise). */
    public static AuditLog record(Integer actorUserId, AuditAction action, AuditEntityKind entityType,
                                  Integer entityId, LocalDateTime timestamp) {
        if (actorUserId == null || action == null || entityType == null || entityId == null || timestamp == null) {
            throw new IllegalArgumentException("An audit row needs an actor, an action, an entity type, an entity id and a timestamp.");
        }
        AuditLog row = new AuditLog();
        row.userId = actorUserId;
        row.action = action;
        row.entityType = entityType;
        row.entityId = entityId;
        row.timestamp = timestamp;
        return row;
    }

    public Integer getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Integer getUserId() { return userId; }
    public AuditAction getAction() { return action; }
    public AuditEntityKind getEntityType() { return entityType; }
    public Integer getEntityId() { return entityId; }
}
