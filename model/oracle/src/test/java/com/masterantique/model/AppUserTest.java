package com.masterantique.model;

import static com.masterantique.model.Fixtures.T0;
import static com.masterantique.model.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** New users, rename and soft delete. */
class AppUserTest {

    @Test
    void createMakesAnActiveUserWithNoPasswordWhoMustReset() {
        AppUser u = AppUser.create("  employee9 ", UserKind.Employee, T0);
        assertThat(u.getName()).isEqualTo("employee9");
        assertThat(u.getDiscriminator()).isEqualTo("Employee");
        assertThat(u.isKind(UserKind.Employee)).isTrue();
        assertThat(u.isKind(UserKind.Customer)).isFalse();
        assertThat(u.getCreatedAt()).isEqualTo(T0);
        assertThat(u.getPasswordHash()).isNull();
        assertThat(u.isMustResetPassword()).isTrue();
        assertThat(u.isDeleted()).isFalse();
    }

    @Test
    void usernameIsRequiredAndAtMost256Characters() {
        assertThatThrownBy(() -> AppUser.create(" ", UserKind.Customer, T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Username is required.");
        assertThatThrownBy(() -> AppUser.create("n".repeat(257), UserKind.Customer, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AppUser.create("n".repeat(256), UserKind.Customer, T0).getName()).hasSize(256);
    }

    @Test
    void renameTrimsAndValidates() {
        AppUser u = user(7, "customer3", UserKind.Customer);
        u.rename("  Customer Three ");
        assertThat(u.getName()).isEqualTo("Customer Three");
        assertThatThrownBy(() -> u.rename("")).isInstanceOf(IllegalArgumentException.class);
        assertThat(u.getName()).isEqualTo("Customer Three");
    }

    @Test
    void softDeleteSetsDeletedAtOnce() {
        AppUser u = user(3, "employee2", UserKind.Employee);
        u.softDelete(T0);
        assertThat(u.isDeleted()).isTrue();
        assertThat(u.getDeletedAt()).isEqualTo(T0);
        assertThat(u.getName()).isEqualTo("employee2");          // the row keeps its name; the index frees it
        assertThatThrownBy(() -> u.softDelete(T0.plusDays(1))).isInstanceOf(IllegalStateException.class);
        assertThat(u.getDeletedAt()).isEqualTo(T0);
    }

    @Test
    void aDeletedUserCannotBeRenamed() {
        AppUser u = user(3, "employee2", UserKind.Employee);
        u.softDelete(T0);
        assertThatThrownBy(() -> u.rename("employee2b")).isInstanceOf(IllegalStateException.class);
    }
}
