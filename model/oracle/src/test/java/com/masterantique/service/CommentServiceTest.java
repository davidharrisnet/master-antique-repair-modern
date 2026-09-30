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

import com.masterantique.api.dto.CommentView;
import com.masterantique.model.AuditAction;
import com.masterantique.model.AuditEntityKind;
import com.masterantique.model.AuditLog;
import com.masterantique.model.Ticket;
import com.masterantique.repo.CommentRepository;
import com.masterantique.repo.TicketRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests of the comment actions (7, 13), with mocked repositories (no database). */
class CommentServiceTest {

    private TestData d;
    private TicketRepository tickets;
    private CommentRepository comments;
    private CommentService service;

    @BeforeEach
    void setUp() {
        d = new TestData();
        tickets = mock(TicketRepository.class);
        comments = mock(CommentRepository.class);
        service = new CommentService(d.actingUser, tickets, comments, d.audit, TestData.CLOCK);
    }

    @Test
    void action7_customerCommentsOnOwnCompletedTicketAndAuditsAddCommentWithoutText() {
        Ticket ticket = TestData.completed(7, d.customer, d.employee, NOW.minusDays(1));
        when(tickets.findByIdAndCustomer_Id(7, d.customer.getId())).thenReturn(Optional.of(ticket));
        TestData.saveAssigns(comments, 30);

        CommentView view = service.add(d.customer.getId(), 7, "  Secret text: the chair is lovely  ");

        assertThat(view.id()).isEqualTo(30);
        assertThat(view.text()).isEqualTo("Secret text: the chair is lovely");
        assertThat(view.authorUsername()).isEqualTo("customer1");
        assertAudit(d.auditRows(), d.customer.getId(), AuditAction.AddComment, AuditEntityKind.Comment, 30);
        // audit rows hold ids and times only: no field of AuditLog can carry the text
        AuditLog row = d.auditRows().get(0);
        assertThat(AuditLog.class.getDeclaredFields()).noneMatch(f -> f.getType() == String.class);
        assertThat(row.toString()).doesNotContain("Secret");
    }

    @Test
    void action7_commentOnATicketThatIsNotCompletedIs409() {
        Ticket ticket = TestData.inProgress(7, d.customer, d.employee, NOW.minusDays(1));
        when(tickets.findByIdAndCustomer_Id(7, d.customer.getId())).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.add(d.customer.getId(), 7, "Any news?"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Comments can only be added to completed tickets.");
        verify(comments, never()).save(any());
        assertThat(d.auditRows()).isEmpty();
    }

    @Test
    void action7_someoneElsesTicketIs404() {
        when(tickets.findByIdAndCustomer_Id(7, d.customer.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.add(d.customer.getId(), 7, "Hello")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void action7_invalidTextIs400() {
        Ticket ticket = TestData.completed(7, d.customer, d.employee, NOW.minusDays(1));
        when(tickets.findByIdAndCustomer_Id(7, d.customer.getId())).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.add(d.customer.getId(), 7, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(d.customer.getId(), 7, "x".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(d.customer.getId(), 7, "esc\u001b"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(comments, never()).save(any());
    }

    @Test
    void action13_employeeCommentsOnAssignedCompletedTicket() {
        Ticket ticket = TestData.completed(7, d.customer, d.employee, NOW.minusDays(1));
        when(tickets.findByIdAndAssignee_Id(7, d.employee.getId())).thenReturn(Optional.of(ticket));
        TestData.saveAssigns(comments, 31);

        CommentView view = service.add(d.employee.getId(), 7, "Line one\r\n\tLine two");

        assertThat(view.authorId()).isEqualTo(d.employee.getId());
        assertAudit(d.auditRows(), d.employee.getId(), AuditAction.AddComment, AuditEntityKind.Comment, 31);
        verify(tickets, never()).findByIdAndCustomer_Id(any(), any());
    }

    @Test
    void action13_aTicketNotAssignedToMeIs404() {
        when(tickets.findByIdAndAssignee_Id(7, d.employee.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.add(d.employee.getId(), 7, "Hello")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void action7and13_managersCannotComment() {
        assertThatThrownBy(() -> service.add(d.manager.getId(), 7, "Hello")).isInstanceOf(ForbiddenException.class);
        verify(tickets, never()).findByIdAndAssignee_Id(any(), any());
    }
}
