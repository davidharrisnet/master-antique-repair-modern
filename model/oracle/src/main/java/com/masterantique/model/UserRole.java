package com.masterantique.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One row of {@code user_roles}: the role a user has. Roles decide page access, as in the legacy app
 * ({@code RepairAuthHelper.RequireRole}); every user gets exactly one role when created. Plain id columns, no
 * associations: join to {@link Role} in queries.
 */
@Entity
@Table(name = "user_roles")
@IdClass(UserRoleId.class)
public class UserRole {

    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Id
    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    protected UserRole() {
    }

    public UserRole(Integer userId, Integer roleId) {
        if (userId == null || roleId == null) {
            throw new IllegalArgumentException("A user role needs a user id and a role id.");
        }
        this.userId = userId;
        this.roleId = roleId;
    }

    public Integer getUserId() { return userId; }
    public Integer getRoleId() { return roleId; }
}
