package com.masterantique.service;

import com.masterantique.model.AppUser;
import com.masterantique.model.UserKind;
import java.util.Set;

/** The acting user of a request (active, loaded in the current transaction) and their role names from {@code user_roles}. */
public record Actor(AppUser user, Set<String> roles) {

    public Integer id() {
        return user.getId();
    }

    public boolean hasRole(UserKind role) {
        return roles.contains(role.name());
    }
}
