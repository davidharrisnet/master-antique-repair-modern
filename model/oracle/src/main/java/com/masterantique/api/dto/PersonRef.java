package com.masterantique.api.dto;

/** A user named in a response: id and username only (never a password hash or security stamp). */
public record PersonRef(Integer id, String username) {
}
