package com.masterantique.service;

import com.masterantique.api.dto.CommentMetrics;
import com.masterantique.api.dto.DayCount;
import com.masterantique.api.dto.EmployeeSeries;
import com.masterantique.api.dto.MetricsView;
import com.masterantique.api.dto.NameCount;
import com.masterantique.api.dto.TicketCount;
import com.masterantique.model.AppUser;
import com.masterantique.model.Comment;
import com.masterantique.model.Ticket;
import com.masterantique.model.TicketState;
import com.masterantique.model.UserKind;
import com.masterantique.repo.AppUserRepository;
import com.masterantique.repo.CommentRepository;
import com.masterantique.repo.TicketRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Action 25: the manager's metrics, as the legacy {@code Metrics} page computes them. Nothing when no ticket is
 * completed. Otherwise a window of 7 days ending today (clamped so it starts no earlier than the first completion):
 * completions per day per active employee (zero-filled, employees by username), the period total and average per day,
 * tickets created in the period and per day, the completed/created ratio (null when nothing was created), the most
 * productive day, the days with no completions, the busiest employee, the comments of the period and the most commented
 * ticket. Ties go to the earliest day, the first employee by username, the commenter first by username and the lowest
 * ticket id.
 */
@Service
public class MetricsService {

    static final int WINDOW_DAYS = 7;

    private final ActingUser actingUser;
    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final AppUserRepository users;
    private final Clock clock;

    public MetricsService(ActingUser actingUser, TicketRepository tickets, CommentRepository comments,
                          AppUserRepository users, Clock clock) {
        this.actingUser = actingUser;
        this.tickets = tickets;
        this.comments = comments;
        this.users = users;
        this.clock = clock;
    }

    /** Action 25. */
    @Transactional(readOnly = true)
    public MetricsView metrics(Integer actingUserId) {
        actingUser.require(actingUserId, UserKind.Manager);
        LocalDateTime firstCompleted = tickets.findFirstCompletedDate(TicketState.COMPLETED);
        if (firstCompleted == null) {
            return MetricsView.noData();
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(WINDOW_DAYS - 1);
        if (start.isBefore(firstCompleted.toLocalDate())) {
            start = firstCompleted.toLocalDate();
        }
        if (start.isAfter(today)) {   // a completion dated in the future: keep at least today in the window
            start = today;
        }
        List<LocalDate> days = start.datesUntil(today.plusDays(1)).toList();
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        List<Ticket> completed = tickets.findByStateAndCompletedDateGreaterThanEqualAndCompletedDateLessThan(
                TicketState.COMPLETED, from, to);
        int periodTotal = completed.size();

        List<EmployeeSeries> series = new ArrayList<>();
        for (AppUser e : users.findActiveByDiscriminatorOrderByName(UserKind.Employee.name())) {
            List<Integer> perDay = days.stream().map(d -> (int) completed.stream()
                    .filter(t -> t.isAssignedTo(e.getId()) && t.getCompletedDate().toLocalDate().equals(d))
                    .count()).toList();
            series.add(new EmployeeSeries(e.getId(), e.getName(), perDay,
                    perDay.stream().mapToInt(Integer::intValue).sum()));
        }

        List<DayCount> perDay = days.stream().map(d -> new DayCount(d, (int) completed.stream()
                .filter(t -> t.getCompletedDate().toLocalDate().equals(d)).count())).toList();
        DayCount mostProductive = perDay.stream().max(Comparator.comparingInt(DayCount::count)
                .thenComparing(DayCount::day, Comparator.reverseOrder())).orElseThrow();
        int quietDays = (int) perDay.stream().filter(d -> d.count() == 0).count();
        NameCount busiest = series.stream()
                .reduce((best, s) -> s.total() > best.total() ? s : best)
                .map(s -> new NameCount(s.username(), s.total()))
                .orElse(null);

        int created = tickets.findBySubmittedDateGreaterThanEqualAndSubmittedDateLessThan(from, to).size();
        Double ratio = created == 0 ? null : (double) periodTotal / created;

        List<Comment> inPeriod = comments.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
        List<Comment> byEmployees = inPeriod.stream().filter(c -> c.getAuthor().isKind(UserKind.Employee)).toList();
        List<Comment> byCustomers = inPeriod.stream().filter(c -> c.getAuthor().isKind(UserKind.Customer)).toList();
        CommentMetrics commentMetrics = new CommentMetrics(inPeriod.size(), (double) inPeriod.size() / days.size(),
                byEmployees.size(), byCustomers.size(), perCommenter(byEmployees), perCommenter(byCustomers));

        TicketCount mostCommented = inPeriod.stream()
                .collect(Collectors.groupingBy(c -> c.getTicket().getId(), Collectors.counting()))
                .entrySet().stream()
                .min(Comparator.<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(en -> new TicketCount(en.getKey(), en.getValue().intValue()))
                .orElse(null);

        return new MetricsView(true, start, today, days, series, periodTotal, (double) periodTotal / days.size(),
                created, (double) created / days.size(), ratio, mostProductive, quietDays, busiest, commentMetrics,
                mostCommented);
    }

    /** Comments per commenter username, most first (then by username). */
    private static List<NameCount> perCommenter(List<Comment> list) {
        Map<String, Long> counts = list.stream().collect(Collectors.groupingBy(
                c -> Objects.toString(c.getAuthor().getName(), ""), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .map(en -> new NameCount(en.getKey(), en.getValue().intValue()))
                .toList();
    }
}
