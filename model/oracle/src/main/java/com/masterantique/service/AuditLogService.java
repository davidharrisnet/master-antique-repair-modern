package com.masterantique.service;

import com.masterantique.api.dto.AuditLogPage;
import com.masterantique.api.dto.AuditLogRow;
import com.masterantique.model.AppUser;
import com.masterantique.model.AuditEntityKind;
import com.masterantique.model.AuditLog;
import com.masterantique.model.UserKind;
import com.masterantique.repo.AppUserRepository;
import com.masterantique.repo.AuditLogRepository;
import com.masterantique.repo.CommentRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Action 14: the audit log, read-only. All rows newest first, optionally only those about one entity id (of any entity
 * type, as the legacy filter), 10 or 20 rows per page. Each row names the actor's username and, for Ticket and Comment
 * rows, the ticket to open (a Comment row's ticket is looked up from the comment; none if it is gone).
 */
@Service
public class AuditLogService {

    private final ActingUser actingUser;
    private final AuditLogRepository auditLogs;
    private final AppUserRepository users;
    private final CommentRepository comments;

    public AuditLogService(ActingUser actingUser, AuditLogRepository auditLogs, AppUserRepository users,
                           CommentRepository comments) {
        this.actingUser = actingUser;
        this.auditLogs = auditLogs;
        this.users = users;
        this.comments = comments;
    }

    /** Action 14: one page ({@code page} from 0; {@code size} 10 or 20, else 400). */
    @Transactional(readOnly = true)
    public AuditLogPage page(Integer actingUserId, Integer entityId, int page, int size) {
        actingUser.require(actingUserId, UserKind.Manager);
        if (size != 10 && size != 20) {
            throw new IllegalArgumentException("The page size must be 10 or 20.");
        }
        if (page < 0) {
            throw new IllegalArgumentException("The page number must be 0 or more.");
        }
        PageRequest request = PageRequest.of(page, size);
        Page<AuditLog> rows = entityId == null
                ? auditLogs.findAllByOrderByTimestampDescIdDesc(request)
                : auditLogs.findByEntityIdOrderByTimestampDescIdDesc(entityId, request);
        Map<Integer, String> names = users.findAllById(
                        rows.getContent().stream().map(AuditLog::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getName, (a, b) -> a));
        List<AuditLogRow> mapped = rows.getContent().stream().map(row -> new AuditLogRow(
                row.getId(), row.getTimestamp(), row.getUserId(), names.get(row.getUserId()),
                row.getAction().name(), row.getAction().ordinal(),
                row.getEntityType().name(), row.getEntityType().ordinal(),
                row.getEntityId(), ticketToOpen(row))).toList();
        return new AuditLogPage(mapped, page, size, rows.getTotalElements(), rows.getTotalPages());
    }

    private Integer ticketToOpen(AuditLog row) {
        if (row.getEntityType() == AuditEntityKind.Ticket) {
            return row.getEntityId();
        }
        if (row.getEntityType() == AuditEntityKind.Comment) {
            return comments.findTicketIdById(row.getEntityId()).orElse(null);
        }
        return null;
    }
}
