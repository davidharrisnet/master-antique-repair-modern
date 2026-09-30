package com.masterantique.service;

import static com.masterantique.service.TestData.NOW;
import static com.masterantique.service.TicketServiceTest.assertAudit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.masterantique.api.dto.AccountView;
import com.masterantique.api.dto.AssignedTicketView;
import com.masterantique.api.dto.AuditLogRow;
import com.masterantique.api.dto.CommentView;
import com.masterantique.api.dto.CustomerTicketView;
import com.masterantique.api.dto.PersonDetailView;
import com.masterantique.api.dto.PersonRef;
import com.masterantique.api.dto.SearchResult;
import com.masterantique.api.dto.TicketDetailView;
import com.masterantique.api.dto.UnassignedTicketView;
import com.masterantique.api.dto.UserListItem;
import com.masterantique.api.dto.UserRequest;
import com.masterantique.model.AppUser;
import com.masterantique.model.AuditAction;
import com.masterantique.model.AuditEntityKind;
import com.masterantique.model.Role;
import com.masterantique.model.UserKind;
import com.masterantique.model.UserRole;
import com.masterantique.repo.RoleRepository;
import com.masterantique.repo.TicketRepository;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests of the manager's account actions (15-23, 27, 28), with mocked repositories (no database). */
class AccountServiceTest {

    private TestData d;
    private RoleRepository roles;
    private TicketRepository tickets;
    private AccountService service;

    @BeforeEach
    void setUp() {
        d = new TestData();
        roles = mock(RoleRepository.class);
        tickets = mock(TicketRepository.class);
        service = new AccountService(d.actingUser, d.users, roles, d.userRoles, tickets, d.audit, TestData.CLOCK);
        for (UserKind kind : UserKind.values()) {
            Role role = mock(Role.class);
            when(role.getId()).thenReturn(kind.ordinal() + 1);
            when(role.getName()).thenReturn(kind.name());
            when(roles.findByName(kind.name())).thenReturn(Optional.of(role));
        }
    }

    @Test
    void action15_activeEmployeesByUsername() {
        when(d.users.findActiveByDiscriminatorOrderByName("Employee")).thenReturn(List.of(d.employee, d.otherEmployee));

        List<UserListItem> result = service.list(d.manager.getId(), UserKind.Employee, false);

        assertThat(result).extracting(UserListItem::username).containsExactly("employee1", "employee2");
        verify(d.users, never()).findByDiscriminatorOrderByIdAsc(any());
    }

    @Test
    void action19_activeCustomersByUsername_action27PickerIncludesDeleted() {
        AppUser gone = TestData.user(9, "gone", UserKind.Customer);
        gone.softDelete(NOW.minusDays(1));
        when(d.users.findActiveByDiscriminatorOrderByName("Customer")).thenReturn(List.of(d.customer));
        when(d.users.findByDiscriminatorOrderByIdAsc("Customer")).thenReturn(List.of(d.customer, gone));

        assertThat(service.list(d.manager.getId(), UserKind.Customer, false)).hasSize(1);
        assertThat(service.list(d.manager.getId(), UserKind.Customer, true))
                .extracting(UserListItem::id, UserListItem::deleted)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(5, false), org.assertj.core.groups.Tuple.tuple(9, true));
    }

    @Test
    void action15_anEmployeeIsForbidden() {
        assertThatThrownBy(() -> service.list(d.employee.getId(), UserKind.Employee, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void action16_addEmployeeWithoutPasswordWithRoleRowAndAuditCreateUser() {
        when(d.users.existsActiveByName("newbie")).thenReturn(false);
        TestData.saveAssigns(d.users, 50);

        AccountView view = service.add(d.manager.getId(), UserKind.Employee, "  newbie ");

        assertThat(view.id()).isEqualTo(50);
        assertThat(view.username()).isEqualTo("newbie");
        assertThat(view.kind()).isEqualTo("Employee");
        assertThat(view.mustResetPassword()).isTrue();
        assertThat(view.createdAt()).isEqualTo(NOW);
        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(d.users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNull();
        ArgumentCaptor<UserRole> role = ArgumentCaptor.forClass(UserRole.class);
        verify(d.userRoles).save(role.capture());
        assertThat(role.getValue().getUserId()).isEqualTo(50);
        assertThat(role.getValue().getRoleId()).isEqualTo(UserKind.Employee.ordinal() + 1);
        assertAudit(d.auditRows(), d.manager.getId(), AuditAction.CreateUser, AuditEntityKind.User, 50);
    }

    @Test
    void action20_duplicateActiveUsernameIs409() {
        when(d.users.existsActiveByName("customer1")).thenReturn(true);

        assertThatThrownBy(() -> service.add(d.manager.getId(), UserKind.Customer, "customer1"))
                .isInstanceOf(IllegalStateException.class).hasMessage("That username is already taken.");
        verify(d.users, never()).save(any());
        assertThat(d.auditRows()).isEmpty();
    }

    @Test
    void action20_aUsernameFreedBySoftDeleteCanBeReused() {
        // the only holder of "oldname" is soft-deleted, so it is not an active match
        when(d.users.existsActiveByName("oldname")).thenReturn(false);
        TestData.saveAssigns(d.users, 51);

        AccountView view = service.add(d.manager.getId(), UserKind.Customer, "oldname");

        assertThat(view.kind()).isEqualTo("Customer");
        ArgumentCaptor<UserRole> role = ArgumentCaptor.forClass(UserRole.class);
        verify(d.userRoles).save(role.capture());
        assertThat(role.getValue().getRoleId()).isEqualTo(UserKind.Customer.ordinal() + 1);
    }

    @Test
    void action16_blankUsernameIs400() {
        assertThatThrownBy(() -> service.add(d.manager.getId(), UserKind.Employee, "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Username is required.");
    }

    @Test
    void action16_notAuditedWhenTheActorIsNotAManager() {
        // parity with AccountService.LogIfManager: the audit row needs an actor that resolves to a Manager
        AppUser odd = TestData.user(8, "odd", UserKind.Employee);
        d.actor(odd, "Manager");
        when(d.users.existsActiveByName("x")).thenReturn(false);
        TestData.saveAssigns(d.users, 52);

        service.add(odd.getId(), UserKind.Employee, "x");

        assertThat(d.auditRows()).isEmpty();
    }

    @Test
    void action17_renameEmployeeAndAuditEditUser() {
        when(d.users.findByIdAndDiscriminator(2, "Employee")).thenReturn(Optional.of(d.employee));
        when(d.users.existsActiveByNameAndIdNot("Employee One", 2)).thenReturn(false);

        AccountView view = service.rename(d.manager.getId(), UserKind.Employee, 2, "Employee One");

        assertThat(view.username()).isEqualTo("Employee One");
        verify(d.users).saveAndFlush(d.employee);
        assertAudit(d.auditRows(), d.manager.getId(), AuditAction.EditUser, AuditEntityKind.User, 2);
    }

    @Test
    void action17_renameToAnotherActiveUsersNameIs409() {
        when(d.users.findByIdAndDiscriminator(2, "Employee")).thenReturn(Optional.of(d.employee));
        when(d.users.existsActiveByNameAndIdNot("employee2", 2)).thenReturn(true);

        assertThatThrownBy(() -> service.rename(d.manager.getId(), UserKind.Employee, 2, "employee2"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(d.employee.getName()).isEqualTo("employee1");
    }

    @Test
    void action17_notAnEmployeeOrDeletedIs404() {
        when(d.users.findByIdAndDiscriminator(5, "Employee")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rename(d.manager.getId(), UserKind.Employee, 5, "x"))
                .isInstanceOf(NotFoundException.class).hasMessage("That employee no longer exists.");

        d.otherEmployee.softDelete(NOW.minusDays(1));
        when(d.users.findByIdAndDiscriminator(3, "Employee")).thenReturn(Optional.of(d.otherEmployee));
        assertThatThrownBy(() -> service.rename(d.manager.getId(), UserKind.Employee, 3, "x"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void action21_renameCustomerNotFoundMessage() {
        when(d.users.findByIdAndDiscriminator(2, "Customer")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rename(d.manager.getId(), UserKind.Customer, 2, "x"))
                .isInstanceOf(NotFoundException.class).hasMessage("That customer no longer exists.");
    }

    @Test
    void action18_softDeleteEmployeeKeepsRowAndAuditsDeleteUser() {
        when(d.users.findByIdAndDiscriminator(2, "Employee")).thenReturn(Optional.of(d.employee));

        service.delete(d.manager.getId(), UserKind.Employee, 2);

        assertThat(d.employee.isDeleted()).isTrue();
        assertThat(d.employee.getDeletedAt()).isEqualTo(NOW);
        verify(d.users, never()).delete(any());
        assertAudit(d.auditRows(), d.manager.getId(), AuditAction.DeleteUser, AuditEntityKind.User, 2);
    }

    @Test
    void action22_deletingADeletedCustomerIs404() {
        d.customer.softDelete(NOW.minusDays(1));
        when(d.users.findByIdAndDiscriminator(5, "Customer")).thenReturn(Optional.of(d.customer));

        assertThatThrownBy(() -> service.delete(d.manager.getId(), UserKind.Customer, 5))
                .isInstanceOf(NotFoundException.class).hasMessage("That customer no longer exists.");
        assertThat(d.auditRows()).isEmpty();
    }

    @Test
    void action23and28_employeeDetailWithAssignedTicketsByIdDeletedIncluded() {
        List<com.masterantique.model.Ticket> assigned = List.of(
                TestData.inProgress(4, d.customer, d.otherEmployee, NOW.minusDays(9)),
                TestData.inProgress(6, d.customer, d.otherEmployee, NOW.minusDays(8)));
        d.otherEmployee.softDelete(NOW.minusDays(1));
        when(d.users.findByIdAndDiscriminator(3, "Employee")).thenReturn(Optional.of(d.otherEmployee));
        when(tickets.findByAssignee_IdOrderByIdAsc(3)).thenReturn(assigned);

        PersonDetailView view = service.detail(d.manager.getId(), UserKind.Employee, 3);

        assertThat(view.username()).isEqualTo("employee2");
        assertThat(view.deleted()).isTrue();
        assertThat(view.tickets()).extracting(t -> t.id()).containsExactly(4, 6);
    }

    @Test
    void action27_customerDetailWithTicketsNewestFirst() {
        when(d.users.findByIdAndDiscriminator(5, "Customer")).thenReturn(Optional.of(d.customer));
        when(tickets.findByCustomer_IdOrderByIdDesc(5)).thenReturn(List.of(
                TestData.submitted(9, d.customer, NOW), TestData.submitted(2, d.customer, NOW)));

        PersonDetailView view = service.detail(d.manager.getId(), UserKind.Customer, 5);

        assertThat(view.tickets()).extracting(t -> t.id()).containsExactly(9, 2);
        verify(tickets, never()).findByAssignee_IdOrderByIdAsc(any());
    }

    @Test
    void action27_unknownCustomerIsNotFound() {
        when(d.users.findByIdAndDiscriminator(77, "Customer")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(d.manager.getId(), UserKind.Customer, 77))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void action16to21_noRequestOrResponseCarriesAPasswordOrSecurityStamp() {
        // passwords belong to the security component: no DTO has such a field, and a user with a hash maps without it
        ReflectionTestUtils.setField(d.employee, "passwordHash", "{bcrypt}$2a$10$secret");
        when(d.users.findByIdAndDiscriminator(2, "Employee")).thenReturn(Optional.of(d.employee));
        when(d.users.existsActiveByNameAndIdNot("employee1", 2)).thenReturn(false);
        assertThat(service.rename(d.manager.getId(), UserKind.Employee, 2, "employee1").toString())
                .doesNotContain("secret");

        Stream.of(UserRequest.class, AccountView.class, UserListItem.class, PersonDetailView.class, PersonRef.class,
                        CommentView.class, CustomerTicketView.class, AssignedTicketView.class,
                        UnassignedTicketView.class, TicketDetailView.class, AuditLogRow.class, SearchResult.class)
                .flatMap(c -> Stream.of(c.getRecordComponents()))
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .forEach(name -> {
                    assertThat(name).doesNotContain("hash").doesNotContain("securitystamp");
                    if (name.contains("password")) {
                        assertThat(name).isEqualTo("mustresetpassword");   // the flag only, never a password value
                    }
                });
    }
}
