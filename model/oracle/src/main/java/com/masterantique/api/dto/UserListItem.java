package com.masterantique.api.dto;

/** Actions 15, 19 (active users, by username) and the pickers of 27, 28 (all users by id, deleted ones marked). */
public record UserListItem(Integer id, String username, boolean deleted) {
}
