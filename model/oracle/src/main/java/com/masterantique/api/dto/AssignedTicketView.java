package com.masterantique.api.dto;

import com.masterantique.model.TicketState;
import java.time.LocalDateTime;
import java.util.List;

/** Action 10: a ticket assigned to me, with its state and its comments split into employee and customer comments. */
public record AssignedTicketView(Integer id, String description, TicketState state, LocalDateTime submittedDate,
                                 LocalDateTime assignedDate, LocalDateTime completedDate,
                                 List<CommentView> employeeComments, List<CommentView> customerComments) {
}
