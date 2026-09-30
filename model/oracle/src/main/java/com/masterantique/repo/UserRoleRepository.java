package com.masterantique.repo;

import com.masterantique.model.UserRole;
import com.masterantique.model.UserRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(Integer userId);

    /** The names of a user's roles (one per user in practice), for the role check of every action. */
    @Query("select r.name from UserRole ur join Role r on r.id = ur.roleId where ur.userId = :userId order by r.name")
    List<String> findRoleNamesByUserId(@Param("userId") Integer userId);
}
