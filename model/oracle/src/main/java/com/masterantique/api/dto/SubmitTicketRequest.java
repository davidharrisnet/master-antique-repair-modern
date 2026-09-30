package com.masterantique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Action 8: the repair description (the service applies the full text rules after trimming). */
public record SubmitTicketRequest(
        @NotBlank(message = "Description is required.")
        @Size(max = 2000, message = "Description must be at most 2,000 characters.")
        String description) {
}
