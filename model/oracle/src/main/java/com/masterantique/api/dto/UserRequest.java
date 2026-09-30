package com.masterantique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Actions 16, 17, 20, 21: the username only. Passwords belong to the security component and are not accepted here. */
public record UserRequest(
        @NotBlank(message = "Username is required.")
        @Size(max = 256, message = "Username must be at most 256 characters.")
        String username) {
}
