package com.masterantique.api;

/** The request header naming the acting user: a stand-in for authentication until the security component exists. */
public final class ActingUserHeader {

    public static final String NAME = "X-Acting-User-Id";

    public static final String DESCRIPTION = "Id of the user performing the request: an active user (else 400) whose "
            + "role in user_roles allows the action (else 403). A local stand-in for authentication (the server listens "
            + "on 127.0.0.1 only) until the security component exists; not deployable.";

    private ActingUserHeader() {
    }
}
