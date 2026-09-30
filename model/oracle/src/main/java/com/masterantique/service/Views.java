package com.masterantique.service;

import com.masterantique.api.dto.CommentView;
import com.masterantique.api.dto.PersonRef;
import com.masterantique.api.dto.TicketView;
import com.masterantique.model.AppUser;
import com.masterantique.model.Comment;
import com.masterantique.model.Ticket;
import com.masterantique.model.UserKind;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Entity-to-DTO mapping shared by the services (runs inside their transactions; no password fields are ever mapped). */
final class Views {

    private Views() {
    }

    /** Now, to the millisecond (the columns are {@code TIMESTAMP(3)}). */
    static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    static PersonRef person(AppUser user) {
        return user == null ? null : new PersonRef(user.getId(), user.getName());
    }

    static CommentView comment(Comment c) {
        AppUser author = c.getAuthor();
        return new CommentView(c.getId(), author.getId(), author.getName(), c.getText(), c.getCreatedAt());
    }

    static TicketView ticket(Ticket t) {
        return new TicketView(t.getId(), t.getDescription(), t.getState(), t.getSubmittedDate(), t.getAssignedDate(),
                t.getCompletedDate());
    }

    /** The comments (already oldest first) written by users of one kind, as the legacy {@code c.User is Employee}. */
    static List<CommentView> byAuthorKind(List<Comment> comments, UserKind kind) {
        return comments.stream().filter(c -> c.getAuthor().isKind(kind)).map(Views::comment).toList();
    }
}
