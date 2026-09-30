package com.masterantique.api;

import com.masterantique.api.dto.AuditLogPage;
import com.masterantique.api.dto.MetricsView;
import com.masterantique.api.dto.SearchResult;
import com.masterantique.service.AuditLogService;
import com.masterantique.service.MetricsService;
import com.masterantique.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The manager's read-only reports: audit log, metrics, text search. HTTP mapping only. */
@RestController
@RequestMapping("/api")
@Tag(name = "Reports", description = "The manager's audit log, metrics and text search (read-only)")
public class ManagerReportController {

    private final AuditLogService auditLogs;
    private final MetricsService metrics;
    private final SearchService search;

    public ManagerReportController(AuditLogService auditLogs, MetricsService metrics, SearchService search) {
        this.auditLogs = auditLogs;
        this.metrics = metrics;
        this.search = search;
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Action 14: audit log (Manager)",
            description = "All audit rows, newest first, optionally only those whose entity id matches (any entity "
                    + "type); page from 0, size 10 or 20. Each row: timestamp, actor's username, action and entity type "
                    + "(names and legacy codes), entity id, and the ticket to open for Ticket and Comment rows.")
    public AuditLogPage auditLogs(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId,
                                  @RequestParam(required = false) Integer entityId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        return auditLogs.page(actingUserId, entityId, page, size);
    }

    @GetMapping("/metrics")
    @Operation(summary = "Action 25: metrics (Manager)",
            description = "hasData=false if no ticket is completed. Otherwise the 7-day window ending today (starting no "
                    + "earlier than the first completion): completions per day per active employee, period total and "
                    + "average, tickets created and per day, completed/created ratio (null when nothing was created), "
                    + "most productive day, days with no completions, busiest employee, comments, most commented ticket.")
    public MetricsView metrics(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId) {
        return metrics.metrics(actingUserId);
    }

    @GetMapping("/search")
    @Operation(summary = "Action 29: text search in comments and ticket descriptions (Manager)",
            description = "Text trimmed and required (400 \"Enter some text to search for.\"). Matches ignoring case, "
                    + "newest first: type (Comment or Ticket Description), text, author's username, date, ticket id.")
    public List<SearchResult> search(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId,
                                     @RequestParam(required = false) String text) {
        return search.search(actingUserId, text);
    }
}
