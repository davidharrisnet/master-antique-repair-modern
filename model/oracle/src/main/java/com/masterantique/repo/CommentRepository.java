package com.masterantique.repo;

import com.masterantique.model.Comment;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Comments (add-only). The author is loaded where the caller splits comments into employee and customer comments by
 * the author's discriminator.
 */
public interface CommentRepository extends JpaRepository<Comment, Integer> {

    /** One ticket's comments, oldest first, with their authors (actions 6, 10, 26). */
    @EntityGraph(attributePaths = "author")
    List<Comment> findByTicket_IdOrderByCreatedAtAscIdAsc(Integer ticketId);

    /** The comments of several tickets at once, oldest first, with authors (lists of 6 and 10 without N+1 queries). */
    @EntityGraph(attributePaths = {"author", "ticket"})
    List<Comment> findByTicket_IdInOrderByCreatedAtAscIdAsc(Collection<Integer> ticketIds);

    /** The ticket a comment belongs to (the "ticket to open" of a Comment audit row, action 14); empty if gone. */
    @Query("select c.ticket.id from Comment c where c.id = :id")
    Optional<Integer> findTicketIdById(@Param("id") Integer id);

    /**
     * Comments whose text contains the text, ignoring case (as the legacy SQL Server search), newest first, with
     * author and ticket loaded (action 29). LIKE wildcards in the text are escaped.
     */
    @EntityGraph(attributePaths = {"author", "ticket"})
    List<Comment> findByTextContainingIgnoreCaseOrderByCreatedAtDescIdDesc(String text);

    /** Comments created in [from, to), with authors and tickets (metrics, action 25). */
    @EntityGraph(attributePaths = {"author", "ticket"})
    List<Comment> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);
}
