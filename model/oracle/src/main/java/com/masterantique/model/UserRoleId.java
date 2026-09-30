package com.masterantique.model;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@code user_roles} (pk_user_roles: user_id, role_id), for {@code @IdClass}. */
public class UserRoleId implements Serializable {

    private Integer userId;
    private Integer roleId;

    public UserRoleId() {
    }

    public UserRoleId(Integer userId, Integer roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    public Integer getUserId() { return userId; }
    public Integer getRoleId() { return roleId; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof UserRoleId other
                && Objects.equals(userId, other.userId) && Objects.equals(roleId, other.roleId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}
