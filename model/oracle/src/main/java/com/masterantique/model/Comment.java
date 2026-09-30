package com.masterantique.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * One row of {@code comments}. Add-only, as in the legacy app (no edit or delete). Created only by {@link #add}.
 */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "text", length = 2000)
    private String text;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Comment() {
    }

    /**
     * A new comment by {@code author} on {@code ticket}, created {@code now}. The ticket must be COMPLETED
     * ({@link IllegalStateException}: "Comments can only be added to completed tickets.", a 409). The text follows
     * {@link #validText} and is stored trimmed ({@link IllegalArgumentException}, a 400). Who may comment (the
     * ticket's customer, or its assigned employee; never a manager) is the caller's check, with
     * {@code TicketRepository.findByIdAndCustomer_Id} / {@code findByIdAndAssignee_Id} (404 otherwise); an author that
     * is neither the ticket's customer nor its assignee, or is soft-deleted, is refused here too
     * ({@link IllegalArgumentException}).
     */
    public static Comment add(AppUser author, Ticket ticket, String text, LocalDateTime now) {
        if (author == null || ticket == null || now == null) {
            throw new IllegalArgumentException("A comment needs an author, a ticket and a time.");
        }
        if (author.isDeleted()
                || !(ticket.isOwnedBy(author.getId()) || ticket.isAssignedTo(author.getId()))) {
            throw new IllegalArgumentException("Only the ticket's customer or its assigned employee can comment on it.");
        }
        if (ticket.getState() != TicketState.COMPLETED) {
            throw new IllegalStateException("Comments can only be added to completed tickets.");
        }
        Comment comment = new Comment();
        comment.text = validText(text);
        comment.author = author;
        comment.ticket = ticket;
        comment.createdAt = now;
        return comment;
    }

    /**
     * The comment text rules ({@link TextRules#validText}: trimmed, required, at most 2,000 characters and 4,000 UTF-8
     * bytes, no control characters other than CR/LF/TAB): returns the trimmed text or throws
     * {@link IllegalArgumentException}. Use it to check a completion comment before completing the ticket.
     */
    public static String validText(String raw) {
        return TextRules.validText(raw, "Comment");
    }

    public Integer getId() { return id; }
    public AppUser getAuthor() { return author; }
    public Ticket getTicket() { return ticket; }
    public String getText() { return text; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
