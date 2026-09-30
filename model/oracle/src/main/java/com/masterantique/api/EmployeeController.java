package com.masterantique.api;

import com.masterantique.api.dto.AccountView;
import com.masterantique.api.dto.AssignedTicketView;
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

/** Employees: HTTP mapping only; the rules are in {@link AccountService} and {@link TicketService}. */
@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employees", description = "An employee's assigned tickets; the manager's employee accounts and look-up")
public class EmployeeController {

    private final AccountService accounts;
    private final TicketService tickets;

    public EmployeeController(AccountService accounts, TicketService tickets) {
        this.accounts = accounts;
        this.tickets = tickets;
    }

    @GetMapping("/me/tickets")
    @Operation(summary = "Action 10: list my assigned tickets (Employee)",
            description = "Tickets assigned to me, by id, with state and the comments split into employee and customer "
                    + "comments, each oldest first.")
    public List<AssignedTicketView> myTickets(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId) {
        return tickets.myAssignedTickets(actingUserId);
    }

    @GetMapping
    @Operation(summary = "Action 15: list active employees; with includeDeleted=true the picker of action 28 (Manager)",
            description = "Default: employees not soft-deleted, by username. includeDeleted=true: every employee by id, "
                    + "soft-deleted ones included and marked.")
    public List<UserListItem> list(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId,
                                   @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return accounts.list(actingUserId, UserKind.Employee, includeDeleted);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Action 16: add an employee (Manager)",
            description = "Username required, unique among active users (409). No password is set here (security "
                    + "component): the employee must reset it. Role Employee. Audit CreateUser.")
    public AccountView add(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId,
                           @Valid @RequestBody UserRequest body) {
        return accounts.add(actingUserId, UserKind.Employee, body.username());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Action 17: edit an employee - rename only (Manager)",
            description = "Username required, unique among other active users (409); \"That employee no longer "
                    + "exists.\" (404) if the id is not an active employee. The password part belongs to the security "
                    + "component. Audit EditUser.")
    public AccountView rename(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId, @PathVariable Integer id,
                              @Valid @RequestBody UserRequest body) {
        return accounts.rename(actingUserId, UserKind.Employee, id, body.username());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Action 18: delete an employee - soft delete (Manager)",
            description = "Sets the deleted date; tickets, comments and audit rows stay and the username becomes free. "
                    + "404 if the id is not an active employee. Audit DeleteUser.")
    public void delete(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId, @PathVariable Integer id) {
        accounts.delete(actingUserId, UserKind.Employee, id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Actions 23 and 28: view an employee's tickets / look up an employee (Manager)",
            description = "Username, created date, deleted flag and the tickets assigned to them, by id; soft-deleted "
                    + "employees included. 404 \"Not found\".")
    public PersonDetailView detail(@RequestHeader(ActingUserHeader.NAME) Integer actingUserId,
                                   @PathVariable Integer id) {
        return accounts.detail(actingUserId, UserKind.Employee, id);
    }
}
