package com.masterantique.service;

import static com.masterantique.service.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.masterantique.api.dto.SearchResult;
import com.masterantique.model.Ticket;
import com.masterantique.repo.CommentRepository;
import com.masterantique.repo.TicketRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests of the text search (action 29), with mocked repositories (no database). */
class SearchServiceTest {

    private TestData d;
    private CommentRepository comments;
    private TicketRepository tickets;
    private SearchService service;

    @BeforeEach
    void setUp() {
        d = new TestData();
        comments = mock(CommentRepository.class);
        tickets = mock(TicketRepository.class);
        service = new SearchService(d.actingUser, comments, tickets);
    }

    @Test
    void action29_blankSearchTextIs400() {
        assertThatThrownBy(() -> service.search(d.manager.getId(), "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Enter some text to search for.");
        assertThatThrownBy(() -> service.search(d.manager.getId(), null)).isInstanceOf(IllegalArgumentException.class);
        verify(comments, never()).findByTextContainingIgnoreCaseOrderByCreatedAtDescIdDesc(any());
    }

    @Test
    void action29_commentsAndDescriptionsNewestFirstWithAuthorAndTicket() {
        Ticket t7 = TestData.completed(7, d.customer, d.employee, NOW.minusDays(2));   // submitted NOW-3d
        Ticket t8 = TestData.submitted(8, d.otherCustomer, NOW.minusHours(1));
        Ticket undated = TestData.submitted(9, d.customer, NOW);
        ReflectionTestUtils.setField(undated, "submittedDate", null);
        when(comments.findByTextContainingIgnoreCaseOrderByCreatedAtDescIdDesc("chair")).thenReturn(List.of(
                TestData.comment(2, d.customer, t7, "Chair looks new", NOW.minusDays(1)),
                TestData.comment(1, d.employee, t7, "chair glued", NOW.minusDays(2))));
        when(tickets.findByDescriptionContainingIgnoreCaseOrderBySubmittedDateDescIdDesc("chair"))
                .thenReturn(List.of(t8, t7, undated));

        List<SearchResult> results = service.search(d.manager.getId(), "  chair ");

        assertThat(results).extracting(SearchResult::type, SearchResult::ticketId).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Ticket Description", 8),
                org.assertj.core.groups.Tuple.tuple("Comment", 7),
                org.assertj.core.groups.Tuple.tuple("Comment", 7),
                org.assertj.core.groups.Tuple.tuple("Ticket Description", 7),
                org.assertj.core.groups.Tuple.tuple("Ticket Description", 9));
        assertThat(results.get(0).authorUsername()).isEqualTo("customer2");
        assertThat(results.get(1).authorUsername()).isEqualTo("customer1");
        assertThat(results.get(2).authorUsername()).isEqualTo("employee1");
        assertThat(results.get(4).posted()).isNull();
    }

    @Test
    void action29_onEqualDatesCommentsComeFirst() {
        Ticket t7 = TestData.completed(7, d.customer, d.employee, NOW.minusDays(2));
        Ticket t8 = TestData.submitted(8, d.customer, NOW.minusDays(1));
        when(comments.findByTextContainingIgnoreCaseOrderByCreatedAtDescIdDesc("x"))
                .thenReturn(List.of(TestData.comment(1, d.customer, t7, "x", NOW.minusDays(1))));
        when(tickets.findByDescriptionContainingIgnoreCaseOrderBySubmittedDateDescIdDesc("x")).thenReturn(List.of(t8));

        assertThat(service.search(d.manager.getId(), "x")).extracting(SearchResult::type)
                .containsExactly("Comment", "Ticket Description");
    }

    @Test
    void action29_onlyAManagerMaySearch() {
        assertThatThrownBy(() -> service.search(d.employee.getId(), "chair")).isInstanceOf(ForbiddenException.class);
    }
}
