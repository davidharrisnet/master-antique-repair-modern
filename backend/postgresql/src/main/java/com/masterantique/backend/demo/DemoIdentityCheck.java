package com.masterantique.backend.demo;

import com.masterantique.backend.login.IdentityCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * DEMO ONLY (active only with the "demo" profile): accepts one fixed code from configuration (demo.issued-code,
 * default DEMO-1234). Without the profile, RejectingIdentityCheck is used and no password can be changed.
 */
@Component
@Profile("demo")
public class DemoIdentityCheck implements IdentityCheck {

    private static final Logger log = LoggerFactory.getLogger(DemoIdentityCheck.class);

    private final String expectedCode;

    public DemoIdentityCheck(@Value("${demo.issued-code:DEMO-1234}") String expectedCode) {
        this.expectedCode = expectedCode;
    }

    @Override
    public boolean verify(String username, String oneTimeCode) {
        log.warn("DEMO identity check used for '{}': replace DemoIdentityCheck before production", username);
        return expectedCode.equals(oneTimeCode);
    }
}
