package com.masterantique.service;

import com.masterantique.api.dto.CommentView;
import com.masterantique.model.AuditAction;
import com.masterantique.model.AuditEntityKind;
import com.masterantique.model.Comment;
import com.masterantique.model.Ticket;
import com.masterantique.model.UserKind;
import com.masterantique.repo.CommentRepository;
import com.masterantique.repo.TicketRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Actions 7 (a customer comments on their own ticket) and 13 (an employee comments on a ticket assigned to them).
 * Comments are add-only. A ticket that is unknown or not the actor's is 404 (the legacy page did nothing); a ticket that
 * is not COMPLETED is 409; the text rules are 400. Audit AddComment (actor: the commenter, entity: the comment).
 */
@Service
public class CommentService {

    private final ActingUser actingUser;
    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final AuditWriter audit;
    private final Clock clock;

    public CommentService(ActingUser actingUser, TicketRepository tickets, CommentRepository comments, AuditWriter audit,
                          Clock clock) {
        this.actingUser = actingUser;
        this.tickets = tickets;
        this.comments = comments;
        this.audit = audit;
        this.clock = clock;
    }

    /** Actions 7 and 13: add a comment to my ticket (Customer) or to a ticket assigned to me (Employee). */
    @Transactional
    public CommentView add(Integer actingUserId, Integer ticketId, String text) {
        Actor actor = actingUser.require(actingUserId, UserKind.Customer, UserKind.Employee);
        Ticket ticket = (actor.hasRole(UserKind.Customer)
                ? tickets.findByIdAndCustomer_Id(ticketId, actor.id())
                : tickets.findByIdAndAssignee_Id(ticketId, actor.id()))
                .orElseThrow(() -> new NotFoundException(actor.hasRole(UserKind.Customer)
                        ? "Ticket not found among your tickets."
                        : "Ticket not found among the tickets assigned to you."));
        LocalDateTime now = Views.now(clock);
        Comment comment = comments.save(Comment.add(actor.user(), ticket, text, now));
        audit.write(actor.id(), AuditAction.AddComment, AuditEntityKind.Comment, comment.getId(), now);
        return Views.comment(comment);
    }
}
