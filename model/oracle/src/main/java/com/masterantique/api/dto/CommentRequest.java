package com.masterantique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Actions 7 and 13: the comment text (the service applies the full text rules after trimming). */
public record CommentRequest(
        @NotBlank(message = "Comment is required.")
        @Size(max = 2000, message = "Comment must be at most 2,000 characters.")
        String text) {
}
