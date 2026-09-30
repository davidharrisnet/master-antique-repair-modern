package com.masterantique.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserRoleTest {

    @Test
    void compositeKeyEqualityAndRequiredIds() {
        assertThat(new UserRoleId(5, 1)).isEqualTo(new UserRoleId(5, 1)).hasSameHashCodeAs(new UserRoleId(5, 1));
        assertThat(new UserRoleId(5, 1)).isNotEqualTo(new UserRoleId(5, 2));
        UserRole ur = new UserRole(5, 1);
        assertThat(ur.getUserId()).isEqualTo(5);
        assertThat(ur.getRoleId()).isEqualTo(1);
        assertThatThrownBy(() -> new UserRole(null, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userKindNamesAreTheDiscriminatorAndRoleNames() {
        assertThat(UserKind.values()).extracting(Enum::name).containsExactly("Customer", "Employee", "Manager");
    }
}
