package com.masterantique.repo;

import com.masterantique.model.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    /** A role by its exact name: "Customer", "Employee" or "Manager" ({@code UserKind.name()}). */
    Optional<Role> findByName(String name);
}
