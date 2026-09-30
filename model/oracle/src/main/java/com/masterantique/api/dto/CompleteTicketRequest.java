package com.masterantique.api.dto;

import jakarta.validation.constraints.Size;

/** Action 12: an optional completion comment; blank or missing means no comment. */
public record CompleteTicketRequest(
        @Size(max = 2000, message = "Comment must be at most 2,000 characters.")
        String comment) {
}
