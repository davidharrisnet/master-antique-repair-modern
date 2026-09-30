package com.masterantique.model;

import static com.masterantique.model.Fixtures.T0;
import static com.masterantique.model.Fixtures.submitted;
import static com.masterantique.model.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The ticket workflow SUBMITTED -> INPROGRESS -> COMPLETED and its refusals. */
class TicketTest {

    private final AppUser customer = user(5, "customer1", UserKind.Customer);
    private final AppUser employee = user(2, "employee1", UserKind.Employee);
    private final AppUser otherEmployee = user(3, "employee2", UserKind.Employee);
    private final AppUser manager = user(1, "manager", UserKind.Manager);

    @Test
    void submitCreatesASubmittedTicketWithTrimmedDescription() {
        Ticket t = Ticket.submit(customer, "  Refinish the walnut armoire \n", T0);
        assertThat(t.getState()).isEqualTo(TicketState.SUBMITTED);
        assertThat(t.getDescription()).isEqualTo("Refinish the walnut armoire");
        assertThat(t.getCustomer()).isSameAs(customer);
        assertThat(t.getAssignee()).isNull();
        assertThat(t.getSubmittedDate()).isEqualTo(T0);
        assertThat(t.getAssignedDate()).isNull();
        assertThat(t.getCompletedDate()).isNull();
        assertThat(t.isOwnedBy(5)).isTrue();
        assertThat(t.isOwnedBy(6)).isFalse();
    }

    @Test
    void submitAppliesTheDescriptionRules() {
        assertThatThrownBy(() -> Ticket.submit(customer, "  ", T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Description is required.");
        assertThatThrownBy(() -> Ticket.submit(customer, "x".repeat(2001), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ticket.submit(customer, "esc\u001B", T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAnActiveCustomerSubmits() {
        assertThatThrownBy(() -> Ticket.submit(employee, "Fix", T0)).isInstanceOf(IllegalArgumentException.class);
        customer.softDelete(T0);
        assertThatThrownBy(() -> Ticket.submit(customer, "Fix", T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void takeAssignsAndStartsWork() {
        Ticket t = submitted(40, customer);
        t.take(employee, T0.plusHours(1));
        assertThat(t.getState()).isEqualTo(TicketState.INPROGRESS);
        assertThat(t.getAssignee()).isSameAs(employee);
        assertThat(t.getAssignedDate()).isEqualTo(T0.plusHours(1));
        assertThat(t.isAssignedTo(2)).isTrue();
        assertThat(t.isAssignedTo(3)).isFalse();
    }

    @Test
    void takingATakenTicketIsAConflict() {
        Ticket t = submitted(40, customer);
        t.take(employee, T0);
        assertThatThrownBy(() -> t.take(otherEmployee, T0.plusMinutes(5)))
                .isInstanceOf(IllegalStateException.class).hasMessage("This ticket has already been taken.");
        assertThat(t.getAssignee()).isSameAs(employee);
    }

    @Test
    void onlyAnActiveEmployeeTakes() {
        Ticket t = submitted(40, customer);
        assertThatThrownBy(() -> t.take(manager, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> t.take(customer, T0)).isInstanceOf(IllegalArgumentException.class);
        employee.softDelete(T0);
        assertThatThrownBy(() -> t.take(employee, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(t.getState()).isEqualTo(TicketState.SUBMITTED);
    }

    @Test
    void completeByTheAssignee() {
        Ticket t = submitted(40, customer);
        t.take(employee, T0);
        t.complete(employee, T0.plusDays(2));
        assertThat(t.getState()).isEqualTo(TicketState.COMPLETED);
        assertThat(t.getCompletedDate()).isEqualTo(T0.plusDays(2));
    }

    @Test
    void completingAgainIsAConflictAndKeepsTheDate() {
        Ticket t = submitted(40, customer);
        t.take(employee, T0);
        t.complete(employee, T0.plusDays(2));
        assertThatThrownBy(() -> t.complete(employee, T0.plusDays(3)))
                .isInstanceOf(IllegalStateException.class).hasMessage("This ticket is already completed.");
        assertThat(t.getCompletedDate()).isEqualTo(T0.plusDays(2));
    }

    @Test
    void completingSomeoneElsesOrAnUnassignedTicketIsRefused() {
        Ticket unassigned = submitted(40, customer);
        assertThatThrownBy(() -> unassigned.complete(employee, T0)).isInstanceOf(IllegalStateException.class);
        Ticket taken = submitted(41, customer);
        taken.take(employee, T0);
        assertThatThrownBy(() -> taken.complete(otherEmployee, T0))
                .isInstanceOf(IllegalStateException.class).hasMessage("This ticket is not assigned to you.");
        assertThat(taken.getState()).isEqualTo(TicketState.INPROGRESS);
    }
}
