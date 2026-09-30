package com.masterantique.api.dto;

import java.time.LocalDateTime;

/** Action 29: a match. {@code type} is "Comment" or "Ticket Description"; the author is the commenter or the ticket's customer. */
public record SearchResult(String type, String text, String authorUsername, LocalDateTime posted, Integer ticketId) {
}
