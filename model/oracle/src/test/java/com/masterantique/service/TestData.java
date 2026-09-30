package com.masterantique.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.masterantique.model.AppUser;
import com.masterantique.model.AuditLog;
import com.masterantique.model.Comment;
import com.masterantique.model.Ticket;
import com.masterantique.model.UserKind;
import com.masterantique.repo.AppUserRepository;
import com.masterantique.repo.AuditLogRepository;
import com.masterantique.repo.UserRoleRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Shared set-up for the service tests: real entities with ids set (as loaded from the database), mocked repositories,
 * a fixed clock, and the real {@link ActingUser} and {@link AuditWriter} over mocks. No database, no Spring context.
 */
final class TestData {

    /** The fixed "now" of every test: 30 September 2026, 10:00 local time. */
    static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 10, 0);
    static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    final AppUserRepository users = mock(AppUserRepository.class);
    final UserRoleRepository userRoles = mock(UserRoleRepository.class);
    final AuditLogRepository auditLogs = mock(AuditLogRepository.class);
    final ActingUser actingUser = new ActingUser(users, userRoles);
    final AuditWriter audit = new AuditWriter(auditLogs);

    final AppUser manager = user(1, "manager", UserKind.Manager);
    final AppUser employee = user(2, "employee1", UserKind.Employee);
    final AppUser otherEmployee = user(3, "employee2", UserKind.Employee);
    final AppUser customer = user(5, "customer1", UserKind.Customer);
    final AppUser otherCustomer = user(6, "customer2", UserKind.Customer);

    TestData() {
        for (AppUser u : List.of(manager, employee, otherEmployee, customer, otherCustomer)) {
            actor(u, u.getDiscriminator());
        }
    }

    /** Makes {@code user} resolvable as an acting user holding {@code role} in user_roles. */
    void actor(AppUser user, String role) {
        when(users.findByIdAndDeletedAtIsNull(user.getId())).thenReturn(Optional.of(user));
        when(userRoles.findRoleNamesByUserId(user.getId())).thenReturn(List.of(role));
    }

    static AppUser user(int id, String name, UserKind kind) {
        AppUser user = AppUser.create(name, kind, NOW.minusDays(60));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    static Ticket submitted(int id, AppUser customer, LocalDateTime when) {
        Ticket ticket = Ticket.submit(customer, "Reglue the chair leg " + id, when);
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }

    static Ticket inProgress(int id, AppUser customer, AppUser employee, LocalDateTime when) {
        Ticket ticket = submitted(id, customer, when);
        ticket.take(employee, when.plusHours(1));
        return ticket;
    }

    static Ticket completed(int id, AppUser customer, AppUser employee, LocalDateTime completedAt) {
        Ticket ticket = inProgress(id, customer, employee, completedAt.minusDays(1));
        ticket.complete(employee, completedAt);
        return ticket;
    }

    static Comment comment(int id, AppUser author, Ticket ticket, String text, LocalDateTime when) {
        Comment comment = Comment.add(author, ticket, text, when);
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    /** Makes a repository's save return its argument with this id set (IDENTITY columns get their id on insert). */
    static <T> void saveAssigns(org.springframework.data.repository.CrudRepository<T, ?> repo, int id) {
        when(repo.save(any())).thenAnswer(inv -> {
            Object entity = inv.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", id);
            return entity;
        });
    }

    /** The audit rows written so far. */
    List<AuditLog> auditRows() {
        ArgumentCaptor<AuditLog> rows = ArgumentCaptor.forClass(AuditLog.class);
        org.mockito.Mockito.verify(auditLogs, org.mockito.Mockito.atLeast(0)).save(rows.capture());
        return rows.getAllValues();
    }
}
