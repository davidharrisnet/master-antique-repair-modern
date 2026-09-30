package com.masterantique.service;

import static com.masterantique.service.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.masterantique.api.dto.AssignedTicketView;
import com.masterantique.api.dto.CustomerTicketView;
import com.masterantique.api.dto.TicketDetailView;
import com.masterantique.api.dto.TicketView;
import com.masterantique.api.dto.UnassignedTicketView;
import com.masterantique.model.AuditAction;
import com.masterantique.model.AuditEntityKind;
import com.masterantique.model.AuditLog;
import com.masterantique.model.Comment;
import com.masterantique.model.Ticket;
import com.masterantique.model.TicketState;
import com.masterantique.repo.CommentRepository;
import com.masterantique.repo.TicketRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Unit tests of the ticket actions (6, 8-12, 24, 26), with mocked repositories (no database). */
class TicketServiceTest {

    private TestData d;
    private TicketRepository tickets;
    private CommentRepository comments;
    private TicketService service;

    @BeforeEach
    void setUp() {
        d = new TestData();
        tickets = mock(TicketRepository.class);
        comments = mock(CommentRepository.class);
        service = new TicketService(d.actingUser, tickets, comments, d.audit, TestData.CLOCK);
    }

    @Test
    void action6_myTicketsNewestFirstWithCommentsSplitOldestFirst() {
        Ticket newer = TestData.completed(12, d.customer, d.employee, NOW.minusDays(1));
        Ticket older = TestData.submitted(11, d.customer, NOW.minusDays(5));
        when(tickets.findByCustomer_IdOrderByIdDesc(d.customer.getId())).thenReturn(List.of(newer, older));
        Comment e1 = TestData.comment(1, d.employee, newer, "Done", NOW.minusHours(20));
        Comment c1 = TestData.comment(2, d.customer, newer, "Thanks", NOW.minusHours(10));
        Comment e2 = TestData.comment(3, d.employee, newer, "Welcome", NOW.minusHours(5));
        when(comments.findByTicket_IdInOrderByCreatedAtAscIdAsc(List.of(12, 11))).thenReturn(List.of(e1, c1, e2));

        List<CustomerTicketView> result = service.myTickets(d.customer.getId());

        assertThat(result).extracting(CustomerTicketView::id).containsExactly(12, 11);
        assertThat(result.get(0).employeeComments()).extracting(c -> c.id()).containsExactly(1, 3);
        assertThat(result.get(0).customerComments()).extracting(c -> c.id()).containsExactly(2);
        assertThat(result.get(1).employeeComments()).isEmpty();
        assertThat(result.get(1).state()).isEqualTo(TicketState.SUBMITTED);
    }

    @Test
    void action6_anEmployeeIsForbidden() {
        assertThatThrownBy(() -> service.myTickets(d.employee.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void action8_submitCreatesSubmittedTicketAndAuditsCreateTicket() {
        TestData.saveAssigns(tickets, 40);

        TicketView view = service.submit(d.customer.getId(), "  Refinish the walnut table  ");

        assertThat(view.id()).isEqualTo(40);
        assertThat(view.state()).isEqualTo(TicketState.SUBMITTED);
        assertThat(view.description()).isEqualTo("Refinish the walnut table");
        assertThat(view.submittedDate()).isEqualTo(NOW);
        assertAudit(d.auditRows(), d.customer.getId(), AuditAction.CreateTicket, AuditEntityKind.Ticket, 40);
    }

    @Test
    void action8_blankOrTooLongDescriptionIs400AndNothingIsWritten() {
        assertThatThrownBy(() -> service.submit(d.customer.getId(), "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Description is required.");
        assertThatThrownBy(() -> service.submit(d.customer.getId(), "x".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Description must be at most 2,000 characters.");
        verify(tickets, never()).save(any());
        assertThat(d.auditRows()).isEmpty();
    }

    @Test
    void action8_aManagerIsForbidden() {
        assertThatThrownBy(() -> service.submit(d.manager.getId(), "Fix it"))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("Customer");
    }

    @Test
    void action9_employeeSeesUnassignedTicketsWithoutCustomer() {
        when(tickets.findByAssigneeIsNullOrderByIdAsc())
                .thenReturn(List.of(TestData.submitted(7, d.customer, NOW), TestData.submitted(8, d.otherCustomer, NOW)));

        List<UnassignedTicketView> result = service.unassigned(d.employee.getId());

        assertThat(result).extracting(UnassignedTicketView::id).containsExactly(7, 8);
        assertThat(result).allSatisfy(t -> assertThat(t.customer()).isNull());
    }

    @Test
    void action24_managerSeesUnassignedTicketsWithTheirCustomer() {
        when(tickets.findByAssigneeIsNullOrderByIdAsc()).thenReturn(List.of(TestData.submitted(7, d.customer, NOW)));

        List<UnassignedTicketView> result = service.unassigned(d.manager.getId());

        assertThat(result.get(0).customer().username()).isEqualTo("customer1");
    }

    @Test
    void action9_aCustomerIsForbidden() {
        assertThatThrownBy(() -> service.unassigned(d.customer.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void action10_myAssignedTicketsByIdWithStateAndSplitComments() {
        Ticket done = TestData.completed(3, d.customer, d.employee, NOW.minusDays(1));
        Ticket busy = TestData.inProgress(9, d.otherCustomer, d.employee, NOW.minusDays(2));
        when(tickets.findByAssignee_IdOrderByIdAsc(d.employee.getId())).thenReturn(List.of(done, busy));
        Comment c = TestData.comment(4, d.customer, done, "Looks great", NOW.minusHours(1));
        when(comments.findByTicket_IdInOrderByCreatedAtAscIdAsc(List.of(3, 9))).thenReturn(List.of(c));

        List<AssignedTicketView> result = service.myAssignedTickets(d.employee.getId());

        assertThat(result).extracting(AssignedTicketView::id).containsExactly(3, 9);
        assertThat(result).extracting(AssignedTicketView::state)
                .containsExactly(TicketState.COMPLETED, TicketState.INPROGRESS);
        assertThat(result.get(0).customerComments()).extracting(x -> x.text()).containsExactly("Looks great");
    }

    @Test
    void action11_takeSetsInProgressAssignedNowAndAuditsAssignTicket() {
        Ticket ticket = TestData.submitted(7, d.customer, NOW.minusDays(1));
        when(tickets.findById(7)).thenReturn(Optional.of(ticket));

        TicketView view = service.take(d.employee.getId(), 7);

        assertThat(view.state()).isEqualTo(TicketState.INPROGRESS);
        assertThat(view.assignedDate()).isEqualTo(NOW);
        assertThat(ticket.isAssignedTo(d.employee.getId())).isTrue();
        assertAudit(d.auditRows(), d.employee.getId(), AuditAction.AssignTicket, AuditEntityKind.Ticket, 7);
    }

    @Test
    void action11_takingATakenTicketIs409AndNotAudited() {
        Ticket ticket = TestData.inProgress(7, d.customer, d.otherEmployee, NOW.minusDays(1));
        when(tickets.findById(7)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.take(d.employee.getId(), 7))
                .isInstanceOf(IllegalStateException.class).hasMessage("This ticket has already been taken.");
        assertThat(ticket.isAssignedTo(d.otherEmployee.getId())).isTrue();
        assertThat(d.auditRows()).isEmpty();
    }

    @Test
    void action11_unknownTicketIs404() {
        when(tickets.findById(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.take(d.employee.getId(), 99)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void action11_managerCannotTakeTickets() {
        assertThatThrownBy(() -> service.take(d.manager.getId(), 7)).isInstanceOf(ForbiddenException.class);
        verify(tickets, never()).findById(any());
    }

    @Test
    void action12_completeWithCommentAddsTheCommentAndAuditsCompleteTicketOnly() {
        Ticket ticket = TestData.inProgress(7, d.customer, d.employee, NOW.minusDays(1));
        when(tickets.findByIdAndAssignee_Id(7, d.employee.getId())).thenReturn(Optional.of(ticket));

        TicketView view = service.complete(d.employee.getId(), 7, "  Glued and clamped  ");

        assertThat(view.state()).isEqualTo(TicketState.COMPLETED);
        assertThat(view.completedDate()).isEqualTo(NOW);
        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(comments).save(saved.capture());
        assertThat(saved.getValue().getText()).isEqualTo("Glued and clamped");
        assertThat(saved.getValue().getAuthor()).isSameAs(d.employee);
        List<AuditLog> rows = d.auditRows();
        assertThat(rows).hasSize(1);
        assertAudit(rows, d.employee.getId(), AuditAction.CompleteTicket, AuditEntityKind.Ticket, 7);
    }

    @Test
    void action12_blankCommentCompletesWithoutComment() {
        Ticket ticket = TestData.inProgress(7, d.customer, d.employee, NOW.minusDays(1));
        when(tickets.findByIdAndAssignee_Id(7, d.employee.getId())).thenReturn(Optional.of(ticket));

        service.complete(d.employee.getId(), 7, "   ");

        assertThat(ticket.getState()).isEqualTo(TicketState.COMPLETED);
        verify(comments, never()).save(any());
    }

    @Test
    void action12_aTicketNotAssignedToMeIs404() {
        when(tickets.findByIdAndAssignee_Id(7, d.employee.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.complete(d.employee.getId(), 7, null)).isInstanceOf(NotFoundException.class);
        assertThat(d.auditRows()).isEmpty();
    }

    @Test
    void action12_completingACompletedTicketIs409AndKeepsTheDate() {
        Ticket ticket = TestData.completed(7, d.customer, d.employee, NOW.minusDays(3));
        when(tickets.findByIdAndAssignee_Id(7, d.employee.getId())).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.complete(d.employee.getId(), 7, null))
                .isInstanceOf(IllegalStateException.class).hasMessage("This ticket is already completed.");
        assertThat(ticket.getCompletedDate()).isEqualTo(NOW.minusDays(3));
        assertThat(d.auditRows()).isEmpty();
    }

    @Test
    void action12_anInvalidCommentIs400AndNothingChanges() {
        Ticket ticket = TestData.inProgress(7, d.customer, d.employee, NOW.minusDays(1));
        when(tickets.findByIdAndAssignee_Id(7, d.employee.getId())).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.complete(d.employee.getId(), 7, "x".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.complete(d.employee.getId(), 7, "bell\u0007"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ticket.getState()).isEqualTo(TicketState.INPROGRESS);
        verify(comments, never()).save(any());
        assertThat(d.auditRows()).isEmpty();
    }

    @Test
    void action26_pickerListsEveryTicketById() {
        when(tickets.findAllByOrderByIdAsc())
                .thenReturn(List.of(TestData.submitted(1, d.customer, NOW), TestData.submitted(2, d.customer, NOW)));
        assertThat(service.allTickets(d.manager.getId())).extracting(t -> t.id()).containsExactly(1, 2);
    }

    @Test
    void action26_detailHasPeopleDatesAndSplitComments() {
        Ticket ticket = TestData.completed(7, d.customer, d.employee, NOW.minusDays(1));
        when(tickets.findWithPeopleById(7)).thenReturn(Optional.of(ticket));
        when(comments.findByTicket_IdOrderByCreatedAtAscIdAsc(7)).thenReturn(List.of(
                TestData.comment(1, d.employee, ticket, "Done", NOW.minusHours(3)),
                TestData.comment(2, d.customer, ticket, "Thanks", NOW.minusHours(2))));

        TicketDetailView view = service.ticket(d.manager.getId(), 7);

        assertThat(view.customer().username()).isEqualTo("customer1");
        assertThat(view.assignee().username()).isEqualTo("employee1");
        assertThat(view.completedDate()).isEqualTo(NOW.minusDays(1));
        assertThat(view.employeeComments()).extracting(c -> c.id()).containsExactly(1);
        assertThat(view.customerComments()).extracting(c -> c.id()).containsExactly(2);
    }

    @Test
    void action26_unknownTicketIsNotFound() {
        when(tickets.findWithPeopleById(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ticket(d.manager.getId(), 99))
                .isInstanceOf(NotFoundException.class).hasMessage("Not found");
    }

    @Test
    void action26_anEmployeeIsForbidden() {
        assertThatThrownBy(() -> service.ticket(d.employee.getId(), 7)).isInstanceOf(ForbiddenException.class);
    }

    static void assertAudit(List<AuditLog> rows, Integer actor, AuditAction action, AuditEntityKind kind, Integer id) {
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.getUserId()).isEqualTo(actor);
            assertThat(r.getAction()).isEqualTo(action);
            assertThat(r.getEntityType()).isEqualTo(kind);
            assertThat(r.getEntityId()).isEqualTo(id);
            assertThat(r.getTimestamp()).isEqualTo(NOW);
        });
    }
}
