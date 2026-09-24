package com.masterantique.login;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The safe default until a real identity check exists: nobody can set a first password, so no account can be
 * taken over by someone who only knows a username. The "demo" profile replaces it with DemoIdentityCheck.
 * TODO(Phase 2): a single-use, expiring code issued by a manager, or a reset link sent to a verified email.
 */
@Component
@Profile("!demo")
public class RejectingIdentityCheck implements IdentityCheck {

    @Override
    public boolean verify(String username, String oneTimeCode) {
        return false;
    }
}
