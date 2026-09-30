package com.masterantique.api;

import com.masterantique.api.dto.AccountView;
import com.masterantique.api.dto.CustomerTicketView;
import com.masterantique.api.dto.PersonDetailView;
import com.masterantique.api.dto.UserListItem;
import com.masterantique.api.dto.UserRequest;
import com.masterantique.model.UserKind;
import com.masterantique.service.AccountService;
import com.masterantique.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Customers: HTTP mapping only; the rules are in {@link AccountService} and {@link TicketService}. */
@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "A customer's own tickets; the manager's customer accounts and look-up")
public class CustomerController {

    private final AccountService accounts;
    private final TicketService tickets;

    public CustomerController(AccountService accounts, TicketService tickets) {
        this.accounts = accounts;
        this.tickets = tickets;
    }

    @GetMapping("/me/tickets")
    @Operation(summary = "Action 6: list my tickets (Customer)",
            description = "Newest first (id descending), each with its comments split into employee and customer "
                    + "comments, each oldest first.")
    public List<CustomerTicketView> myTickets(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId) {
        return tickets.myTickets(actingUserId);
    }

    @GetMapping
    @Operation(summary = "Action 19: list active customers; with includeDeleted=true the picker of action 27 (Manager)",
            description = "Default: customers not soft-deleted, by username. includeDeleted=true: every customer by id, "
                    + "soft-deleted ones included and marked.")
    public List<UserListItem> list(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId,
                                   @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return accounts.list(actingUserId, UserKind.Customer, includeDeleted);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Action 20: add a customer (Manager)",
            description = "Username required, unique among active users (409). No password is set here (security "
                    + "component): the customer must reset it. Role Customer. Audit CreateUser.")
    public AccountView add(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId,
                           @Valid @RequestBody UserRequest body) {
        return accounts.add(actingUserId, UserKind.Customer, body.username());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Action 21: edit a customer - rename only (Manager)",
            description = "Username required, unique among other active users (409); \"That customer no longer "
                    + "exists.\" (404) if the id is not an active customer. The password part belongs to the security "
                    + "component. Audit EditUser.")
    public AccountView rename(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId, @PathVariable Integer id,
                              @Valid @RequestBody UserRequest body) {
        return accounts.rename(actingUserId, UserKind.Customer, id, body.username());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Action 22: delete a customer - soft delete (Manager)",
            description = "Sets the deleted date; tickets, comments and audit rows stay and the username becomes free. "
                    + "404 if the id is not an active customer. Audit DeleteUser.")
    public void delete(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId, @PathVariable Integer id) {
        accounts.delete(actingUserId, UserKind.Customer, id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Action 27: look up a customer (Manager)",
            description = "Username, created date, deleted flag and tickets, newest first; soft-deleted customers "
                    + "included. 404 \"Not found\".")
    public PersonDetailView detail(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId,
                                   @PathVariable Integer id) {
        return accounts.detail(actingUserId, UserKind.Customer, id);
    }
}
