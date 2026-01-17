package nl.ak.skillswap.userservice.repository;

import nl.ak.skillswap.userservice.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByExternalId(String externalId);
    Optional<User> findByEmail(String email);
    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Search users by display name (case-insensitive, partial match).
     * Only returns active users who allow matching.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
          AND u.active = true
          AND u.allowMatching = true
          AND LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))
          AND u.id != :excludeUserId
        ORDER BY u.displayName
    """)
    List<User> searchByDisplayName(
            @Param("query") String query,
            @Param("excludeUserId") UUID excludeUserId,
            Pageable pageable
    );

    /**
     * Find users by their IDs, only active users who allow matching.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.id IN :userIds
          AND u.deletedAt IS NULL
          AND u.active = true
          AND u.allowMatching = true
    """)
    List<User> findActiveMatchableByIds(@Param("userIds") List<UUID> userIds);

    /**
     * Check if user exists and is active (for message-service validation).
     */
    boolean existsByIdAndDeletedAtIsNullAndActiveTrue(UUID id);

    /**
     * Check if user exists by external ID (Keycloak sub) and is active.
     */
    boolean existsByExternalIdAndDeletedAtIsNullAndActiveTrue(String externalId);

    /**
     * Find user by external ID (Keycloak sub) - for looking up users by message-service's user IDs.
     */
    Optional<User> findByExternalIdAndDeletedAtIsNull(String externalId);
}