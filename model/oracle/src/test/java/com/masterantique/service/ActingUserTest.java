package com.masterantique.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.masterantique.model.AppUser;
import com.masterantique.model.UserKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of the X-Acting-User-Id stand-in used by every action (6-29): unknown or deleted user 400, wrong role 403,
 * the role taken from user_roles (as RepairAuthHelper.RequireRole), not from the discriminator.
 */
class ActingUserTest {

    private TestData d;

    @BeforeEach
    void setUp() {
        d = new TestData();
    }

    @Test
    void actions6to29_missingHeaderIs400() {
        assertThatThrownBy(() -> d.actingUser.require(null, UserKind.Manager))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actions6to29_unknownOrSoftDeletedUserIs400() {
        when(d.users.findByIdAndDeletedAtIsNull(99)).thenReturn(Optional.empty());   // deleted users are not found
        assertThatThrownBy(() -> d.actingUser.require(99, UserKind.Manager))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("X-Acting-User-Id does not name an active user.");
    }

    @Test
    void actions6to29_wrongRoleIs403() {
        assertThatThrownBy(() -> d.actingUser.require(d.customer.getId(), UserKind.Manager))
                .isInstanceOf(ForbiddenException.class).hasMessage("This action needs the Manager role.");
        assertThatThrownBy(() -> d.actingUser.require(d.manager.getId(), UserKind.Customer, UserKind.Employee))
                .isInstanceOf(ForbiddenException.class).hasMessage("This action needs the Customer or Employee role.");
    }

    @Test
    void actions6to29_theRoleComesFromUserRolesNotTheDiscriminator() {
        AppUser noRole = TestData.user(8, "norole", UserKind.Manager);
        when(d.users.findByIdAndDeletedAtIsNull(8)).thenReturn(Optional.of(noRole));
        when(d.userRoles.findRoleNamesByUserId(8)).thenReturn(List.of());
        assertThatThrownBy(() -> d.actingUser.require(8, UserKind.Manager)).isInstanceOf(ForbiddenException.class);

        Actor actor = d.actingUser.require(d.manager.getId(), UserKind.Manager);
        assertThat(actor.id()).isEqualTo(1);
        assertThat(actor.hasRole(UserKind.Manager)).isTrue();
    }
}
