package com.masterantique.service;

/** The ticket, comment target or user does not exist, or is not the acting user's to act on (HTTP 404). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
