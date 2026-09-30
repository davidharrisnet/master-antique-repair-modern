package com.masterantique.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One row of {@code tickets}. The legacy workflow: SUBMITTED ({@link #submit}) -> INPROGRESS ({@link #take}) ->
 * COMPLETED ({@link #complete}); no edit, delete, reassignment or reopening. Invalid input throws
 * {@link IllegalArgumentException}; a transition from the wrong state throws {@link IllegalStateException}.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.ORDINAL)                          // NUMBER(10) 0/1/2
    @Column(name = "state", nullable = false)
    private TicketState state;

    @Column(name = "description", length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")                          // assigned employee; NULL while SUBMITTED
    private AppUser assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private AppUser customer;

    @Column(name = "submitted_date")
    private LocalDateTime submittedDate;

    @Column(name = "assigned_date")
    private LocalDateTime assignedDate;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    protected Ticket() {
    }

    /**
     * A new SUBMITTED ticket for an active customer, submitted {@code now}. The description follows
     * {@link TextRules#validText} (trimmed, required, at most 2,000 characters and 4,000 UTF-8 bytes, no control
     * characters other than CR/LF/TAB) and is stored trimmed; {@link IllegalArgumentException} otherwise, also when
     * {@code customer} is not an active Customer.
     */
    public static Ticket submit(AppUser customer, String description, LocalDateTime now) {
        requireActive(customer, UserKind.Customer);
        if (now == null) {
            throw new IllegalArgumentException("A ticket needs a submission time.");
        }
        Ticket ticket = new Ticket();
        ticket.description = TextRules.validText(description, "Description");
        ticket.customer = customer;
        ticket.state = TicketState.SUBMITTED;
        ticket.submittedDate = now;
        return ticket;
    }

    /**
     * "Assign to Me": the employee takes this unassigned ticket; it becomes INPROGRESS, assigned {@code now}.
     * {@link IllegalStateException} if it already has an assignee or is not SUBMITTED ("already taken", a 409);
     * {@link IllegalArgumentException} if {@code employee} is not an active Employee.
     */
    public void take(AppUser employee, LocalDateTime now) {
        requireActive(employee, UserKind.Employee);
        if (now == null) {
            throw new IllegalArgumentException("Taking a ticket needs a time.");
        }
        if (assignee != null || state != TicketState.SUBMITTED) {
            throw new IllegalStateException("This ticket has already been taken.");
        }
        this.assignee = employee;
        this.state = TicketState.INPROGRESS;
        this.assignedDate = now;
    }

    /**
     * The assigned employee completes the ticket; it becomes COMPLETED, completed {@code now}.
     * {@link IllegalStateException} if it is not assigned to {@code employee} (callers should find the ticket with
     * {@code TicketRepository.findByIdAndAssignee_Id} first and answer 404) or is already COMPLETED (409; the legacy
     * app re-completed it and moved the date). {@link IllegalArgumentException} if {@code employee} is not an active
     * Employee. An optional completion comment is the caller's: validate it with {@link Comment#validText} before
     * calling this, then add it with {@link Comment#add}.
     */
    public void complete(AppUser employee, LocalDateTime now) {
        requireActive(employee, UserKind.Employee);
        if (now == null) {
            throw new IllegalArgumentException("Completing a ticket needs a time.");
        }
        if (!isAssignedTo(employee.getId())) {
            throw new IllegalStateException("This ticket is not assigned to you.");
        }
        if (state == TicketState.COMPLETED) {
            throw new IllegalStateException("This ticket is already completed.");
        }
        this.state = TicketState.COMPLETED;
        this.completedDate = now;
    }

    /** True if the ticket is assigned to the user with this id (compares ids; safe on lazy proxies). */
    public boolean isAssignedTo(Integer userId) {
        return userId != null && assignee != null && Objects.equals(assignee.getId(), userId);
    }

    /** True if the ticket belongs to the customer with this id. */
    public boolean isOwnedBy(Integer customerId) {
        return customerId != null && customer != null && Objects.equals(customer.getId(), customerId);
    }

    private static void requireActive(AppUser user, UserKind kind) {
        if (user == null || !user.isKind(kind) || user.isDeleted()) {
            throw new IllegalArgumentException("An active " + kind.name().toLowerCase() + " is required.");
        }
    }

    public Integer getId() { return id; }
    public TicketState getState() { return state; }
    public String getDescription() { return description; }
    public AppUser getAssignee() { return assignee; }
    public AppUser getCustomer() { return customer; }
    public LocalDateTime getSubmittedDate() { return submittedDate; }
    public LocalDateTime getAssignedDate() { return assignedDate; }
    public LocalDateTime getCompletedDate() { return completedDate; }
}
