package com.masterantique.api.dto;

import java.time.LocalDateTime;

/** Action 14: one audit row. {@code ticketId} is the ticket to open (Ticket rows: the entity id; Comment rows: the comment's ticket, null if gone; User rows: null). */
public record AuditLogRow(Integer id, LocalDateTime timestamp, Integer actorUserId, String actorUsername, String action,
                          int actionCode, String entityType, int entityTypeCode, Integer entityId, Integer ticketId) {
}
