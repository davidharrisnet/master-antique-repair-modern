package com.masterantique.api;

import com.masterantique.api.dto.CommentRequest;
import com.masterantique.api.dto.CommentView;
import com.masterantique.api.dto.CompleteTicketRequest;
import com.masterantique.api.dto.SubmitTicketRequest;
import com.masterantique.api.dto.TicketDetailView;
import com.masterantique.api.dto.TicketPickerItem;
import com.masterantique.api.dto.TicketView;
import com.masterantique.api.dto.UnassignedTicketView;
import com.masterantique.service.CommentService;
import com.masterantique.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tickets: HTTP mapping only; the rules are in {@link TicketService} and {@link CommentService}. */
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets", description = "The repair workflow: submit, take, complete, comment; the manager's ticket look-up")
public class TicketController {

    private final TicketService tickets;
    private final CommentService comments;

    public TicketController(TicketService tickets, CommentService comments) {
        this.tickets = tickets;
        this.comments = comments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Action 8: submit a repair (Customer)",
            description = "Creates a SUBMITTED ticket for me, submitted now. Description: required, trimmed, at most "
                    + "2,000 characters, no control characters other than CR, LF and TAB. Audit CreateTicket.")
    public TicketView submit(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId,
                             @Valid @RequestBody SubmitTicketRequest body) {
        return tickets.submit(actingUserId, body.description());
    }

    @GetMapping("/unassigned")
    @Operation(summary = "Actions 9 and 24: list unassigned tickets (Employee; Manager with each ticket's customer)",
            description = "Tickets with no assignee, by id. The customer is filled only for a Manager (action 24).")
    public List<UnassignedTicketView> unassigned(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId) {
        return tickets.unassigned(actingUserId);
    }

    @PostMapping("/{id}/take")
    @Operation(summary = "Action 11: take a ticket, \"Assign to Me\" (Employee)",
            description = "Only an unassigned ticket (409 otherwise; 404 if unknown). It becomes INPROGRESS, assigned "
                    + "to me now. Audit AssignTicket.")
    public TicketView take(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId, @PathVariable Integer id) {
        return tickets.take(actingUserId, id);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Action 12: mark a ticket complete, with an optional comment (Employee)",
            description = "Only a ticket assigned to me (404 otherwise); already COMPLETED 409. It becomes COMPLETED, "
                    + "completed now; a non-blank comment is added with the text rules (an invalid one is 400 and "
                    + "nothing changes). Audit CompleteTicket only.")
    public TicketView complete(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId, @PathVariable Integer id,
                               @Valid @RequestBody(required = false) CompleteTicketRequest body) {
        return tickets.complete(actingUserId, id, body == null ? null : body.comment());
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Actions 7 and 13: add a comment to my ticket (Customer) or my assigned ticket (Employee)",
            description = "Only on my own ticket (Customer) or a ticket assigned to me (Employee), else 404; only when "
                    + "it is COMPLETED, else 409. Text: required, trimmed, at most 2,000 characters, no control "
                    + "characters other than CR, LF and TAB. Audit AddComment.")
    public CommentView comment(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId, @PathVariable Integer id,
                               @Valid @RequestBody CommentRequest body) {
        return comments.add(actingUserId, id, body.text());
    }

    @GetMapping
    @Operation(summary = "Action 26: look up a ticket - the picker, every ticket by id (Manager)")
    public List<TicketPickerItem> all(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId) {
        return tickets.allTickets(actingUserId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Action 26: look up a ticket by id (Manager)",
            description = "Description, state, customer, assignee, the three dates and the comments split into "
                    + "employee and customer comments, each oldest first. 404 \"Not found\" for an unknown id.")
    public TicketDetailView one(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId, @PathVariable Integer id) {
        return tickets.ticket(actingUserId, id);
    }
}
