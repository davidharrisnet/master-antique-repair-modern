package com.masterantique.service;

import com.masterantique.api.dto.AccountView;
import com.masterantique.api.dto.PersonDetailView;
import com.masterantique.api.dto.UserListItem;
import com.masterantique.model.AppUser;
import com.masterantique.model.AuditAction;
import com.masterantique.model.AuditEntityKind;
import com.masterantique.model.Role;
import com.masterantique.model.Ticket;
import com.masterantique.model.UserKind;
import com.masterantique.model.UserRole;
import com.masterantique.repo.AppUserRepository;
import com.masterantique.repo.RoleRepository;
import com.masterantique.repo.TicketRepository;
import com.masterantique.repo.UserRoleRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The manager's account actions on employees and customers: list (15, 19), add (16, 20), edit (17, 21), delete (18, 22),
 * and the look-ups (23, 27, 28). Passwords are not handled here (the security component): new users get none and must
 * reset. Usernames are unique among active users (409); a user that is not of the kind, or is soft-deleted, "no longer
 * exists" (404). Account changes are audited only when the actor is a Manager ({@code AccountService.LogIfManager}).
 */
@Service
public class AccountService {

    static final String USERNAME_TAKEN = "That username is already taken.";

    private final ActingUser actingUser;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final TicketRepository tickets;
    private final AuditWriter audit;
    private final Clock clock;

    public AccountService(ActingUser actingUser, AppUserRepository users, RoleRepository roles,
                          UserRoleRepository userRoles, TicketRepository tickets, AuditWriter audit, Clock clock) {
        this.actingUser = actingUser;
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.tickets = tickets;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Actions 15 and 19: active users of the kind, by username; with {@code includeDeleted} the pickers of 27 and 28:
     * every user of the kind by id, soft-deleted ones included and marked.
     */
    @Transactional(readOnly = true)
    public List<UserListItem> list(Integer actingUserId, UserKind kind, boolean includeDeleted) {
        actingUser.require(actingUserId, UserKind.Manager);
        List<AppUser> found = includeDeleted
                ? users.findByDiscriminatorOrderByIdAsc(kind.name())
                : users.findActiveByDiscriminatorOrderByName(kind.name());
        return found.stream().map(u -> new UserListItem(u.getId(), u.getName(), u.isDeleted())).toList();
    }

    /**
     * Actions 16 and 20: add an employee or customer, created now, with no password and must reset, and the matching
     * {@code user_roles} row; audit CreateUser (actor: the manager, entity: the new user).
     */
    @Transactional
    public AccountView add(Integer actingUserId, UserKind kind, String username) {
        Actor actor = actingUser.require(actingUserId, UserKind.Manager);
        String name = AppUser.validName(username);
        if (users.existsActiveByName(name)) {
            throw new IllegalStateException(USERNAME_TAKEN);
        }
        Role role = roles.findByName(kind.name())
                .orElseThrow(() -> new NoSuchElementException("The role " + kind.name() + " is missing from roles."));
        LocalDateTime now = Views.now(clock);
        AppUser user = users.save(AppUser.create(name, kind, now));
        userRoles.save(new UserRole(user.getId(), role.getId()));
        auditIfManager(actor, AuditAction.CreateUser, user.getId(), now);
        return account(user);
    }

    /**
     * Actions 17 and 21: rename an employee or customer (unique among the other active users); the password part of the
     * legacy form belongs to the security component. Audit EditUser.
     */
    @Transactional
    public AccountView rename(Integer actingUserId, UserKind kind, Integer userId, String username) {
        Actor actor = actingUser.require(actingUserId, UserKind.Manager);
        String name = AppUser.validName(username);
        AppUser user = existing(kind, userId);
        if (users.existsActiveByNameAndIdNot(name, user.getId())) {
            throw new IllegalStateException(USERNAME_TAKEN);
        }
        user.rename(name);
        users.saveAndFlush(user);   // a unique-index race surfaces here as DataIntegrityViolationException (409)
        auditIfManager(actor, AuditAction.EditUser, user.getId(), Views.now(clock));
        return account(user);
    }

    /**
     * Actions 18 and 22: soft delete (deleted now); tickets, comments and audit rows stay and the username becomes free.
     * Audit DeleteUser.
     */
    @Transactional
    public void delete(Integer actingUserId, UserKind kind, Integer userId) {
        Actor actor = actingUser.require(actingUserId, UserKind.Manager);
        AppUser user = existing(kind, userId);
        LocalDateTime now = Views.now(clock);
        user.softDelete(now);
        users.save(user);
        auditIfManager(actor, AuditAction.DeleteUser, user.getId(), now);
    }

    /**
     * Actions 23 and 28 (employee) and 27 (customer): the user's username, created date, deleted flag and tickets (an
     * employee's assigned tickets by id; a customer's tickets newest first). Soft-deleted users are included.
     */
    @Transactional(readOnly = true)
    public PersonDetailView detail(Integer actingUserId, UserKind kind, Integer userId) {
        actingUser.require(actingUserId, UserKind.Manager);
        AppUser user = users.findByIdAndDiscriminator(userId, kind.name())
                .orElseThrow(() -> new NotFoundException("Not found"));
        List<Ticket> list = kind == UserKind.Employee
                ? tickets.findByAssignee_IdOrderByIdAsc(user.getId())
                : tickets.findByCustomer_IdOrderByIdDesc(user.getId());
        return new PersonDetailView(user.getId(), user.getName(), user.getDiscriminator(), user.getCreatedAt(),
                user.isDeleted(), list.stream().map(Views::ticket).toList());
    }

    /** An active user of the kind; a soft-deleted or other user "no longer exists" (404). */
    private AppUser existing(UserKind kind, Integer userId) {
        return users.findByIdAndDiscriminator(userId, kind.name())
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new NotFoundException(notFound(kind)));
    }

    private static String notFound(UserKind kind) {
        return "That " + kind.name().toLowerCase() + " no longer exists.";
    }

    private void auditIfManager(Actor actor, AuditAction action, Integer userId, LocalDateTime now) {
        if (actor.user().isKind(UserKind.Manager)) {
            audit.write(actor.id(), action, AuditEntityKind.User, userId, now);
        }
    }

    private static AccountView account(AppUser u) {
        return new AccountView(u.getId(), u.getName(), u.getDiscriminator(), u.getCreatedAt(), u.isDeleted(),
                u.isMustResetPassword());
    }
}
