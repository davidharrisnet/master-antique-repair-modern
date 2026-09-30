package com.masterantique.api.dto;

import com.masterantique.model.TicketState;
import java.time.LocalDateTime;

/** A ticket without comments: the result of actions 8, 11 and 12, and the ticket lists of actions 23, 27 and 28. */
public record TicketView(Integer id, String description, TicketState state, LocalDateTime submittedDate,
                         LocalDateTime assignedDate, LocalDateTime completedDate) {
}
