package com.masterantique.api.dto;

import java.time.LocalDateTime;

/** Actions 9 and 24: an unassigned ticket; {@code customer} is filled only for a Manager (action 24), null for an Employee. */
public record UnassignedTicketView(Integer id, String description, LocalDateTime submittedDate, PersonRef customer) {
}
