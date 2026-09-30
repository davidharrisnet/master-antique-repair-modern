package com.masterantique.service;

import com.masterantique.api.dto.AssignedTicketView;
import com.masterantique.api.dto.CustomerTicketView;
import com.masterantique.api.dto.TicketDetailView;
import com.masterantique.api.dto.TicketPickerItem;
import com.masterantique.api.dto.TicketView;
import com.masterantique.api.dto.UnassignedTicketView;
import com.masterantique.model.AuditAction;
import com.masterantique.model.AuditEntityKind;
import com.masterantique.model.Comment;
import com.masterantique.model.Ticket;
import com.masterantique.model.UserKind;
import com.masterantique.repo.CommentRepository;
import com.masterantique.repo.TicketRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ticket actions: 6 (my tickets), 8 (submit), 9 and 24 (unassigned), 10 (assigned to me), 11 (take), 12 (complete)
 * and 26 (look up a ticket). The workflow rules are the entity's ({@link Ticket}); this class resolves the acting user,
 * finds the ticket (404 when it is unknown or not the actor's), writes the audit row and maps to DTOs.
 */
@Service
public class TicketService {

    private final ActingUser actingUser;
    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final AuditWriter audit;
    private final Clock clock;

    public TicketService(ActingUser actingUser, TicketRepository tickets, CommentRepository comments, AuditWriter audit,
                         Clock clock) {
        this.actingUser = actingUser;
        this.tickets = tickets;
        this.comments = comments;
        this.audit = audit;
        this.clock = clock;
    }

    /** Action 6: my tickets, newest first (id descending), each with its comments split, oldest first. */
    @Transactional(readOnly = true)
    public List<CustomerTicketView> myTickets(Integer actingUserId) {
        Actor actor = actingUser.require(actingUserId, UserKind.Customer);
        List<Ticket> mine = tickets.findByCustomer_IdOrderByIdDesc(actor.id());
        Map<Integer, List<Comment>> byTicket = commentsOf(mine);
        return mine.stream().map(t -> {
            List<Comment> cs = byTicket.getOrDefault(t.getId(), List.of());
            return new CustomerTicketView(t.getId(), t.getDescription(), t.getState(), t.getSubmittedDate(),
                    Views.byAuthorKind(cs, UserKind.Employee), Views.byAuthorKind(cs, UserKind.Customer));
        }).toList();
    }

    /** Action 8: submit a repair; SUBMITTED, submitted now; audit CreateTicket (actor: the customer). */
    @Transactional
    public TicketView submit(Integer actingUserId, String description) {
        Actor actor = actingUser.require(actingUserId, UserKind.Customer);
        LocalDateTime now = Views.now(clock);
        Ticket ticket = tickets.save(Ticket.submit(actor.user(), description, now));
        audit.write(actor.id(), AuditAction.CreateTicket, AuditEntityKind.Ticket, ticket.getId(), now);
        return Views.ticket(ticket);
    }

    /**
     * Actions 9 (Employee) and 24 (Manager): tickets with no assignee, by id; the customer is included only for a
     * Manager, as only the manager page shows it.
     */
    @Transactional(readOnly = true)
    public List<UnassignedTicketView> unassigned(Integer actingUserId) {
        Actor actor = actingUser.require(actingUserId, UserKind.Employee, UserKind.Manager);
        boolean withCustomer = actor.hasRole(UserKind.Manager);
        return tickets.findByAssigneeIsNullOrderByIdAsc().stream()
                .map(t -> new UnassignedTicketView(t.getId(), t.getDescription(), t.getSubmittedDate(),
                        withCustomer ? Views.person(t.getCustomer()) : null))
                .toList();
    }

    /** Action 10: the tickets assigned to me, by id, with state and comments split, oldest first. */
    @Transactional(readOnly = true)
    public List<AssignedTicketView> myAssignedTickets(Integer actingUserId) {
        Actor actor = actingUser.require(actingUserId, UserKind.Employee);
        List<Ticket> mine = tickets.findByAssignee_IdOrderByIdAsc(actor.id());
        Map<Integer, List<Comment>> byTicket = commentsOf(mine);
        return mine.stream().map(t -> {
            List<Comment> cs = byTicket.getOrDefault(t.getId(), List.of());
            return new AssignedTicketView(t.getId(), t.getDescription(), t.getState(), t.getSubmittedDate(),
                    t.getAssignedDate(), t.getCompletedDate(),
                    Views.byAuthorKind(cs, UserKind.Employee), Views.byAuthorKind(cs, UserKind.Customer));
        }).toList();
    }

    /**
     * Action 11: take an unassigned ticket ("Assign to Me"): INPROGRESS, assigned now; audit AssignTicket. Unknown
     * ticket 404; already taken 409 (the legacy page silently did nothing).
     */
    @Transactional
    public TicketView take(Integer actingUserId, Integer ticketId) {
        Actor actor = actingUser.require(actingUserId, UserKind.Employee);
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket not found."));
        LocalDateTime now = Views.now(clock);
        ticket.take(actor.user(), now);
        audit.write(actor.id(), AuditAction.AssignTicket, AuditEntityKind.Ticket, ticket.getId(), now);
        return Views.ticket(ticket);
    }

    /**
     * Action 12: complete a ticket assigned to me: COMPLETED, completed now, plus the optional comment (text rules of
     * action 7). Not mine or unknown 404; already COMPLETED 409; an invalid comment 400 before anything changes. Audit
     * CompleteTicket only: the completion comment gets no AddComment row, as in the legacy app.
     */
    @Transactional
    public TicketView complete(Integer actingUserId, Integer ticketId, String comment) {
        Actor actor = actingUser.require(actingUserId, UserKind.Employee);
        Ticket ticket = tickets.findByIdAndAssignee_Id(ticketId, actor.id())
                .orElseThrow(() -> new NotFoundException("Ticket not found among the tickets assigned to you."));
        String text = comment == null || comment.isBlank() ? null : Comment.validText(comment);
        LocalDateTime now = Views.now(clock);
        ticket.complete(actor.user(), now);
        if (text != null) {
            comments.save(Comment.add(actor.user(), ticket, text, now));
        }
        audit.write(actor.id(), AuditAction.CompleteTicket, AuditEntityKind.Ticket, ticket.getId(), now);
        return Views.ticket(ticket);
    }

    /** Action 26 (picker): every ticket by id, id and description. */
    @Transactional(readOnly = true)
    public List<TicketPickerItem> allTickets(Integer actingUserId) {
        actingUser.require(actingUserId, UserKind.Manager);
        return tickets.findAllByOrderByIdAsc().stream()
                .map(t -> new TicketPickerItem(t.getId(), t.getDescription()))
                .toList();
    }

    /** Action 26: one ticket with customer, assignee, dates and split comments; "Not found" for an unknown id. */
    @Transactional(readOnly = true)
    public TicketDetailView ticket(Integer actingUserId, Integer ticketId) {
        actingUser.require(actingUserId, UserKind.Manager);
        Ticket t = tickets.findWithPeopleById(ticketId).orElseThrow(() -> new NotFoundException("Not found"));
        List<Comment> cs = comments.findByTicket_IdOrderByCreatedAtAscIdAsc(t.getId());
        return new TicketDetailView(t.getId(), t.getDescription(), t.getState(), Views.person(t.getCustomer()),
                Views.person(t.getAssignee()), t.getSubmittedDate(), t.getAssignedDate(), t.getCompletedDate(),
                Views.byAuthorKind(cs, UserKind.Employee), Views.byAuthorKind(cs, UserKind.Customer));
    }

    /** The comments of these tickets in one query, grouped by ticket id, each list oldest first. */
    private Map<Integer, List<Comment>> commentsOf(List<Ticket> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return comments.findByTicket_IdInOrderByCreatedAtAscIdAsc(list.stream().map(Ticket::getId).toList()).stream()
                .collect(Collectors.groupingBy(c -> c.getTicket().getId(), Collectors.toList()));
    }
}
