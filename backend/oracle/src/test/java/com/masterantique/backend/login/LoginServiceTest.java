package com.masterantique.backend.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.masterantique.backend.model.AppUser;
import com.masterantique.backend.repo.AppUserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit tests of the sign-in rules, with the repository and the entity mocked (no database). */
class LoginServiceTest {

    private static final String GOOD_PASSWORD = "Walnut-Armoire-1887";

    private AppUserRepository users;
    private AppUser migrated;          // as every user is after the migration: no hash, must reset

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        migrated = mock(AppUser.class);
        when(migrated.getName()).thenReturn("customer1");
        when(migrated.isMustResetPassword()).thenReturn(true);
        when(migrated.getPasswordHash()).thenReturn(null);
        when(users.findActiveByName("customer1")).thenReturn(Optional.of(migrated));
        when(users.findActiveByName("nobody")).thenReturn(Optional.empty());
    }

    private LoginService service(IdentityCheck check) {
        return new LoginService(users, check);
    }

    @Test
    void migratedUserMustChangePassword() {
        assertThat(service(new RejectingIdentityCheck()).login("customer1", "anything"))
                .isEqualTo(LoginResult.MUST_CHANGE_PASSWORD);
    }

    @Test
    void unknownUserIsInvalid() {
        assertThat(service(new RejectingIdentityCheck()).login("nobody", "anything")).isEqualTo(LoginResult.INVALID);
    }

    @Test
    void userWithOwnPasswordSignsInOnlyWithIt() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        AppUser settled = mock(AppUser.class);
        when(settled.isMustResetPassword()).thenReturn(false);
        when(settled.getPasswordHash()).thenReturn(encoder.encode(GOOD_PASSWORD));
        when(users.findActiveByName("employee1")).thenReturn(Optional.of(settled));

        LoginService login = service(new RejectingIdentityCheck());
        assertThat(login.login("employee1", GOOD_PASSWORD)).isEqualTo(LoginResult.OK);
        assertThat(login.login("employee1", GOOD_PASSWORD + "x")).isEqualTo(LoginResult.INVALID);
    }

    @Test
    void defaultIdentityCheckRefusesEveryChange() {
        assertThatThrownBy(() -> service(new RejectingIdentityCheck())
                .changePassword("customer1", "any-code", GOOD_PASSWORD, GOOD_PASSWORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The one-time code is not valid.");
        verify(migrated, never()).setNewPassword(anyString(), anyString());
    }

    @Test
    void passwordPolicy() {
        assertThatThrownBy(() -> LoginService.checkPolicy("customer1", "short", "short"))
                .hasMessage("The password must have at least 12 characters.");
        assertThatThrownBy(() -> LoginService.checkPolicy("customer1", GOOD_PASSWORD, GOOD_PASSWORD + "x"))
                .hasMessage("The two passwords do not match.");
        assertThatThrownBy(() -> LoginService.checkPolicy("customer1", "my-Customer1-pass", "my-Customer1-pass"))
                .hasMessage("The password must not contain the username.");
        LoginService.checkPolicy("customer1", GOOD_PASSWORD, GOOD_PASSWORD);   // no exception
    }

    @Test
    void successfulChangeStoresBcryptHashAndNewStamp() {
        service((username, code) -> "CODE-1".equals(code))
                .changePassword("customer1", "CODE-1", GOOD_PASSWORD, GOOD_PASSWORD);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> stamp = ArgumentCaptor.forClass(String.class);
        verify(migrated).setNewPassword(hash.capture(), stamp.capture());
        assertThat(hash.getValue()).startsWith("{bcrypt}").doesNotContain(GOOD_PASSWORD);
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(GOOD_PASSWORD, hash.getValue())).isTrue();
        assertThat(UUID.fromString(stamp.getValue())).isNotNull();
    }
}
