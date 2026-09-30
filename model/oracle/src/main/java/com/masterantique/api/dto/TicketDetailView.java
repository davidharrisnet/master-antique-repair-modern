package com.masterantique.api.dto;

import com.masterantique.model.TicketState;
import java.time.LocalDateTime;
import java.util.List;

/** Action 26: one ticket with its people, the three dates and its comments split into employee and customer comments. */
public record TicketDetailView(Integer id, String description, TicketState state, PersonRef customer, PersonRef assignee,
                               LocalDateTime submittedDate, LocalDateTime assignedDate, LocalDateTime completedDate,
                               List<CommentView> employeeComments, List<CommentView> customerComments) {
}
