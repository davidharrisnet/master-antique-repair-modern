package com.masterantique.api.dto;

import com.masterantique.model.TicketState;
import java.time.LocalDateTime;
import java.util.List;

/** Action 6: one of my tickets, with its comments split into employee and customer comments, each oldest first. */
public record CustomerTicketView(Integer id, String description, TicketState state, LocalDateTime submittedDate,
                                 List<CommentView> employeeComments, List<CommentView> customerComments) {
}
