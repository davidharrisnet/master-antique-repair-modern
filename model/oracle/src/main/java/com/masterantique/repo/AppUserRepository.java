package com.masterantique.repo;

import com.masterantique.model.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Users of every kind (TPH table {@code users}); {@code discriminator} is "Customer", "Employee" or "Manager"
 * ({@code UserKind.name()}). Soft-deleted users have {@code deletedAt} set.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Integer> {

    /**
     * Sign-in lookup. Usernames are unique ignoring case among active users, enforced by the function-based index
     * ix_users_name_active ON users (CASE WHEN deleted_at IS NULL THEN LOWER(name) END). Oracle uses that index only
     * when the query repeats its expression, so the condition below is written exactly that way; it matches
     * active users only, because the expression is NULL for soft-deleted ones.
     */
    @Query("select u from AppUser u where (case when u.deletedAt is null then lower(u.name) end) = lower(:name)")
    Optional<AppUser> findActiveByName(@Param("name") String name);

    /** Is the username taken by an active user (ignoring case)? Same index expression as {@link #findActiveByName}. */
    @Query("select count(u) > 0 from AppUser u where (case when u.deletedAt is null then lower(u.name) end) = lower(:name)")
    boolean existsActiveByName(@Param("name") String name);

    /** As {@link #existsActiveByName}, ignoring the user being renamed. */
    @Query("select count(u) > 0 from AppUser u where (case when u.deletedAt is null then lower(u.name) end) = lower(:name)"
            + " and u.id <> :id")
    boolean existsActiveByNameAndIdNot(@Param("name") String name, @Param("id") Integer id);

    /** An active (not soft-deleted) user by id: the acting user of a request. */
    Optional<AppUser> findByIdAndDeletedAtIsNull(Integer id);

    /** A user of one kind by id, soft-deleted ones included (edit, delete and look-up actions). */
    Optional<AppUser> findByIdAndDiscriminator(Integer id, String discriminator);

    /** Active users of one kind by username, ignoring case (actions 15, 19; the employees of metrics, 25). */
    @Query("select u from AppUser u where u.discriminator = :discriminator and u.deletedAt is null"
            + " order by lower(u.name), u.id")
    List<AppUser> findActiveByDiscriminatorOrderByName(@Param("discriminator") String discriminator);

    /** All users of one kind by id, soft-deleted ones included (pickers of actions 27, 28). */
    List<AppUser> findByDiscriminatorOrderByIdAsc(String discriminator);

    long countByDiscriminator(String discriminator);

    long countByMustResetPasswordTrue();
}
