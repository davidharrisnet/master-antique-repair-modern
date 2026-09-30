package com.masterantique.repo;

import com.masterantique.model.Ticket;
import com.masterantique.model.TicketState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tickets; {@code assignee} is the assigned employee (column user_id), {@code customer} the owner. */
public interface TicketRepository extends JpaRepository<Ticket, Integer> {

    long countByState(TicketState state);

    /** A customer's tickets, newest first (actions 6, 27). */
    List<Ticket> findByCustomer_IdOrderByIdDesc(Integer customerId);

    /** Tickets with no assignee, by id, with their customer loaded (actions 9, 24). */
    @EntityGraph(attributePaths = "customer")
    List<Ticket> findByAssigneeIsNullOrderByIdAsc();

    /** Tickets assigned to an employee, by id (actions 10, 23, 28). */
    List<Ticket> findByAssignee_IdOrderByIdAsc(Integer employeeId);

    /** Every ticket by id (picker of action 26). */
    List<Ticket> findAllByOrderByIdAsc();

    /** One ticket with its customer and assignee loaded (detail of action 26). */
    @EntityGraph(attributePaths = {"customer", "assignee"})
    Optional<Ticket> findWithPeopleById(Integer id);

    /** The ticket if it belongs to this customer (action 7); empty means 404. */
    Optional<Ticket> findByIdAndCustomer_Id(Integer id, Integer customerId);

    /** The ticket if it is assigned to this employee (actions 12, 13); empty means 404. */
    Optional<Ticket> findByIdAndAssignee_Id(Integer id, Integer employeeId);

    /**
     * Tickets whose description contains the text, ignoring case (SQL Server's default collation ignored it in the
     * legacy search), newest first, with the customer loaded (action 29). LIKE wildcards in the text are escaped.
     */
    @EntityGraph(attributePaths = "customer")
    List<Ticket> findByDescriptionContainingIgnoreCaseOrderBySubmittedDateDescIdDesc(String text);

    // Metrics (action 25)

    /** The first completion date, or null when no ticket is completed ("nothing to show"). */
    @Query("select min(t.completedDate) from Ticket t where t.state = :state")
    LocalDateTime findFirstCompletedDate(@Param("state") TicketState state);

    /** Tickets completed in [from, to), with their assignee loaded. */
    @EntityGraph(attributePaths = "assignee")
    List<Ticket> findByStateAndCompletedDateGreaterThanEqualAndCompletedDateLessThan(
            TicketState state, LocalDateTime from, LocalDateTime to);

    /** Tickets submitted in [from, to). */
    List<Ticket> findBySubmittedDateGreaterThanEqualAndSubmittedDateLessThan(LocalDateTime from, LocalDateTime to);
}
