package com.masterantique.service;

/** The acting user's role (from {@code user_roles}) does not allow the action (HTTP 403). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
