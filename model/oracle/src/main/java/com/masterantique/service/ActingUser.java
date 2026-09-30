package com.masterantique.service;

import com.masterantique.model.AppUser;
import com.masterantique.model.UserKind;
import com.masterantique.repo.AppUserRepository;
import com.masterantique.repo.UserRoleRepository;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the {@code X-Acting-User-Id} header, the stand-in for authentication until the security component exists:
 * the id must name an active (not soft-deleted) user, else 400 ({@link IllegalArgumentException}); the action's role
 * is checked against the user's {@code user_roles} rows, as the legacy {@code RepairAuthHelper.RequireRole} checked the
 * role claims built from them (not the discriminator), else 403 ({@link ForbiddenException}).
 */
@Service
public class ActingUser {

    private final AppUserRepository users;
    private final UserRoleRepository userRoles;

    public ActingUser(AppUserRepository users, UserRoleRepository userRoles) {
        this.users = users;
        this.userRoles = userRoles;
    }

    /** The acting user, who must hold one of {@code allowed} roles. */
    @Transactional
    public Actor require(Integer actingUserId, UserKind... allowed) {
        if (actingUserId == null) {
            throw new IllegalArgumentException("The X-Acting-User-Id header is required.");
        }
        AppUser user = users.findByIdAndDeletedAtIsNull(actingUserId)
                .orElseThrow(() -> new IllegalArgumentException("X-Acting-User-Id does not name an active user."));
        Set<String> roles = new LinkedHashSet<>(userRoles.findRoleNamesByUserId(user.getId()));
        Actor actor = new Actor(user, roles);
        if (Arrays.stream(allowed).noneMatch(actor::hasRole)) {
            throw new ForbiddenException("This action needs the "
                    + Arrays.stream(allowed).map(Enum::name).collect(Collectors.joining(" or ")) + " role.");
        }
        return actor;
    }
}
