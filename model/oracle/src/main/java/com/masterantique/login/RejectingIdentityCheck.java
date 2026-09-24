package com.masterantique.login;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The default identity check: refuses every first-password change. Migrated users have no password, so knowing a
 * username must not be enough to take an account over; until a real check exists (a single-use code issued by a
 * manager, or a reset link to a verified email address), no password can be set. The "demo" profile replaces it.
 */
@Component
@Profile("!demo")
public class RejectingIdentityCheck implements IdentityCheck {

    @Override
    public boolean verify(String username, String oneTimeCode) {
        return false;
    }
}
