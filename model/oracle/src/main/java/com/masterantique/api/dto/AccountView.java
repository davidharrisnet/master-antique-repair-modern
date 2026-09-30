package com.masterantique.api.dto;

import java.time.LocalDateTime;

/** The result of actions 16, 17, 20, 21: the account, without any password or security-stamp field. */
public record AccountView(Integer id, String username, String kind, LocalDateTime createdAt, boolean deleted,
                          boolean mustResetPassword) {
}
