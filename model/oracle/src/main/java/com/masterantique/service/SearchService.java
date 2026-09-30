package com.masterantique.service;

import com.masterantique.api.dto.SearchResult;
import com.masterantique.model.UserKind;
import com.masterantique.repo.CommentRepository;
import com.masterantique.repo.TicketRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Action 29: text search over comment text and ticket descriptions (ignoring case, as the legacy SQL Server collation
 * did). The search text is trimmed and required. Results newest first; on equal dates comments come before ticket
 * descriptions, as the legacy page concatenated them before its stable sort.
 */
@Service
public class SearchService {

    static final String COMMENT = "Comment";
    static final String TICKET_DESCRIPTION = "Ticket Description";

    private final ActingUser actingUser;
    private final CommentRepository comments;
    private final TicketRepository tickets;

    public SearchService(ActingUser actingUser, CommentRepository comments, TicketRepository tickets) {
        this.actingUser = actingUser;
        this.comments = comments;
        this.tickets = tickets;
    }

    /** Action 29. */
    @Transactional(readOnly = true)
    public List<SearchResult> search(Integer actingUserId, String text) {
        actingUser.require(actingUserId, UserKind.Manager);
        String q = text == null ? "" : text.strip();
        if (q.isEmpty()) {
            throw new IllegalArgumentException("Enter some text to search for.");
        }
        List<SearchResult> results = new ArrayList<>();
        comments.findByTextContainingIgnoreCaseOrderByCreatedAtDescIdDesc(q).forEach(c -> results.add(
                new SearchResult(COMMENT, c.getText(), c.getAuthor().getName(), c.getCreatedAt(), c.getTicket().getId())));
        tickets.findByDescriptionContainingIgnoreCaseOrderBySubmittedDateDescIdDesc(q).forEach(t -> results.add(
                new SearchResult(TICKET_DESCRIPTION, t.getDescription(),
                        t.getCustomer() == null ? null : t.getCustomer().getName(), t.getSubmittedDate(), t.getId())));
        // List.sort is stable: equal dates keep comments first; a ticket without a submitted date goes last
        results.sort(Comparator.comparing(SearchResult::posted,
                Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())).reversed());
        return results;
    }
}
