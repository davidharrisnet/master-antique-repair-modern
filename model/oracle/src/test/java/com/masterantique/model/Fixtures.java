package com.masterantique.model;

import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/** Real entities with ids set, as they come from the database (no database needed). */
final class Fixtures {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 30, 10, 0);

    private Fixtures() {
    }

    static AppUser user(int id, String name, UserKind kind) {
        AppUser user = AppUser.create(name, kind, T0.minusDays(30));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    static Ticket submitted(int id, AppUser customer) {
        Ticket ticket = Ticket.submit(customer, "Reglue the chair leg", T0);
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }
}
