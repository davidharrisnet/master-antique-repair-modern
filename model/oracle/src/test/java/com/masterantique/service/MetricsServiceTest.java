package com.masterantique.service;

import static com.masterantique.service.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.masterantique.api.dto.DayCount;
import com.masterantique.api.dto.EmployeeSeries;
import com.masterantique.api.dto.MetricsView;
import com.masterantique.api.dto.NameCount;
import com.masterantique.api.dto.TicketCount;
import com.masterantique.model.AppUser;
import com.masterantique.model.Ticket;
import com.masterantique.model.TicketState;
import com.masterantique.model.UserKind;
import com.masterantique.repo.CommentRepository;
import com.masterantique.repo.TicketRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests of the metrics (action 25) on a fixed clock (today = 30 September 2026), mocked repositories. */
class MetricsServiceTest {

    private static final LocalDate TODAY = NOW.toLocalDate();

    private TestData d;
    private TicketRepository tickets;
    private CommentRepository comments;
    private MetricsService service;

    @BeforeEach
    void setUp() {
        d = new TestData();
        tickets = mock(TicketRepository.class);
        comments = mock(CommentRepository.class);
        service = new MetricsService(d.actingUser, tickets, comments, d.users, TestData.CLOCK);
        when(d.users.findActiveByDiscriminatorOrderByName("Employee")).thenReturn(List.of(d.employee, d.otherEmployee));
    }

    private static LocalDateTime on(int dayOfSeptember, int hour) {
        return LocalDateTime.of(2026, 9, dayOfSeptember, hour, 0);
    }

    @Test
    void action25_nothingWhenNoTicketIsCompleted() {
        when(tickets.findFirstCompletedDate(TicketState.COMPLETED)).thenReturn(null);

        MetricsView view = service.metrics(d.manager.getId());

        assertThat(view.hasData()).isFalse();
        assertThat(view.periodTotal()).isNull();
    }

    @Test
    void action25_sevenDayWindowEndingToday() {
        LocalDateTime from = on(24, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 1, 0, 0);
        AppUser former = TestData.user(4, "former", UserKind.Employee);
        Ticket t7 = TestData.completed(7, d.customer, d.employee, on(28, 9));
        Ticket t8 = TestData.completed(8, d.otherCustomer, d.employee, on(28, 15));
        Ticket t9 = TestData.completed(9, d.customer, d.otherEmployee, on(29, 11));
        Ticket t10 = TestData.completed(10, d.customer, former, on(30, 8));
        former.softDelete(on(30, 9));   // a deleted employee's completion counts in the totals, not in the series
        when(tickets.findFirstCompletedDate(TicketState.COMPLETED)).thenReturn(on(1, 12));
        when(tickets.findByStateAndCompletedDateGreaterThanEqualAndCompletedDateLessThan(TicketState.COMPLETED, from, to))
                .thenReturn(List.of(t7, t8, t9, t10));
        when(tickets.findBySubmittedDateGreaterThanEqualAndSubmittedDateLessThan(from, to))
                .thenReturn(List.of(TestData.submitted(20, d.customer, on(25, 9)),
                        TestData.submitted(21, d.customer, on(26, 9))));
        when(comments.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)).thenReturn(List.of(
                TestData.comment(1, d.employee, t7, "a", on(28, 10)),
                TestData.comment(2, d.employee, t7, "b", on(28, 11)),
                TestData.comment(3, d.customer, t7, "c", on(29, 10)),
                TestData.comment(4, d.otherCustomer, t8, "d", on(29, 12))));

        MetricsView view = service.metrics(d.manager.getId());

        assertThat(view.hasData()).isTrue();
        assertThat(view.windowStart()).isEqualTo(TODAY.minusDays(6));
        assertThat(view.windowEnd()).isEqualTo(TODAY);
        assertThat(view.days()).hasSize(7);
        assertThat(view.employeeSeries()).extracting(EmployeeSeries::username).containsExactly("employee1", "employee2");
        assertThat(view.employeeSeries().get(0).completedPerDay()).containsExactly(0, 0, 0, 0, 2, 0, 0);
        assertThat(view.employeeSeries().get(1).completedPerDay()).containsExactly(0, 0, 0, 0, 0, 1, 0);
        assertThat(view.periodTotal()).isEqualTo(4);
        assertThat(view.averageCompletedPerDay()).isCloseTo(4.0 / 7, within(1e-9));
        assertThat(view.createdInPeriod()).isEqualTo(2);
        assertThat(view.averageCreatedPerDay()).isCloseTo(2.0 / 7, within(1e-9));
        assertThat(view.completedToCreatedRatio()).isEqualTo(2.0);
        assertThat(view.mostProductiveDay()).isEqualTo(new DayCount(LocalDate.of(2026, 9, 28), 2));
        assertThat(view.daysWithNoCompletions()).isEqualTo(4);
        assertThat(view.busiestEmployee()).isEqualTo(new NameCount("employee1", 2));
        assertThat(view.comments().total()).isEqualTo(4);
        assertThat(view.comments().averagePerDay()).isCloseTo(4.0 / 7, within(1e-9));
        assertThat(view.comments().byEmployees()).isEqualTo(2);
        assertThat(view.comments().byCustomers()).isEqualTo(2);
        assertThat(view.comments().perEmployee()).containsExactly(new NameCount("employee1", 2));
        assertThat(view.comments().perCustomer())
                .containsExactly(new NameCount("customer1", 1), new NameCount("customer2", 1));
        assertThat(view.mostCommentedTicket()).isEqualTo(new TicketCount(7, 3));
    }

    @Test
    void action25_windowStartsNoEarlierThanTheFirstCompletionAndRatioIsNullWithoutCreatedTickets() {
        when(tickets.findFirstCompletedDate(TicketState.COMPLETED)).thenReturn(on(28, 16));
        LocalDateTime from = on(28, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 1, 0, 0);
        when(tickets.findByStateAndCompletedDateGreaterThanEqualAndCompletedDateLessThan(TicketState.COMPLETED, from, to))
                .thenReturn(List.of(TestData.completed(7, d.customer, d.employee, on(28, 16))));
        when(tickets.findBySubmittedDateGreaterThanEqualAndSubmittedDateLessThan(from, to)).thenReturn(List.of());
        when(comments.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)).thenReturn(List.of());

        MetricsView view = service.metrics(d.manager.getId());

        assertThat(view.windowStart()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(view.days()).hasSize(3);
        assertThat(view.completedToCreatedRatio()).isNull();
        assertThat(view.daysWithNoCompletions()).isEqualTo(2);
        assertThat(view.mostCommentedTicket()).isNull();
        assertThat(view.comments().perCustomer()).isEmpty();
    }

    @Test
    void action25_onlyAManagerMaySeeMetrics() {
        assertThatThrownBy(() -> service.metrics(d.customer.getId())).isInstanceOf(ForbiddenException.class);
    }
}
