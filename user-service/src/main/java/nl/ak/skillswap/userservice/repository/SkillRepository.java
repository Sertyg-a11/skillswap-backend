package nl.ak.skillswap.userservice.repository;

import nl.ak.skillswap.userservice.domain.Skill;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {
    List<Skill> findByUserId(UUID userId);
    List<Skill> findByNameIgnoreCase(String name);

    /**
     * Delete all skills for a user (GDPR deletion).
     * @return number of skills deleted
     */
    @Modifying
    @Query("DELETE FROM Skill s WHERE s.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /**
     * Search for user IDs that have skills matching the query.
     * Returns distinct user IDs of users with matching skills.
     */
    @Query("""
        SELECT DISTINCT s.userId FROM Skill s
        WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
          AND s.userId != :excludeUserId
    """)
    List<UUID> findUserIdsBySkillNameContaining(
            @Param("query") String query,
            @Param("excludeUserId") UUID excludeUserId,
            Pageable pageable
    );

    /**
     * Search for user IDs that have skills in a specific category.
     */
    @Query("""
        SELECT DISTINCT s.userId FROM Skill s
        WHERE LOWER(s.category) = LOWER(:category)
          AND s.userId != :excludeUserId
    """)
    List<UUID> findUserIdsBySkillCategory(
            @Param("category") String category,
            @Param("excludeUserId") UUID excludeUserId,
            Pageable pageable
    );

    /**
     * Get skills for multiple users at once (batch fetch for search results).
     */
    @Query("SELECT s FROM Skill s WHERE s.userId IN :userIds")
    List<Skill> findByUserIdIn(@Param("userIds") List<UUID> userIds);
}
