package com.masterantique.service;

import com.masterantique.model.AuditAction;
import com.masterantique.model.AuditEntityKind;
import com.masterantique.model.AuditLog;
import com.masterantique.repo.AuditLogRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit rows in the legacy format ({@code AuditLogger.Log}): timestamp, actor, action code, entity type code,
 * entity id; never comment text or descriptions. Only the services write audit rows (package-private); the API only
 * reads them (action 14).
 */
@Component
class AuditWriter {

    private final AuditLogRepository auditLogs;

    AuditWriter(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Transactional
    void write(Integer actorUserId, AuditAction action, AuditEntityKind entityType, Integer entityId, LocalDateTime now) {
        auditLogs.save(AuditLog.record(actorUserId, action, entityType, entityId, now));
    }
}
