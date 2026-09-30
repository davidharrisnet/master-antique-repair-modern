package com.masterantique.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Actions 23, 27, 28: a customer or employee with their tickets (a customer's newest first, an employee's by id). */
public record PersonDetailView(Integer id, String username, String kind, LocalDateTime createdAt, boolean deleted,
                               List<TicketView> tickets) {
}
