package com.masterantique.api.dto;

import java.util.List;

/** Action 14: one page of audit rows, newest first. */
public record AuditLogPage(List<AuditLogRow> rows, int page, int size, long totalRows, int totalPages) {
}
