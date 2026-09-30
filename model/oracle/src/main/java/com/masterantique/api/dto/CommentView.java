package com.masterantique.api.dto;

import java.time.LocalDateTime;

/** One comment as the ticket views show it (actions 6, 7, 10, 13, 26). */
public record CommentView(Integer id, Integer authorId, String authorUsername, String text, LocalDateTime createdAt) {
}
