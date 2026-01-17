package nl.ak.skillswap.messageservice.repository;


import nl.ak.skillswap.messageservice.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByUserLowIdAndUserHighId(UUID userLowId, UUID userHighId);

    List<Conversation> findByUserLowIdOrUserHighIdOrderByLastMessageAtDescCreatedAtDesc(
            UUID userLowId,
            UUID userHighId
    );

    // ==================== GDPR Operations ====================

    /**
     * Find all conversations involving a user (for GDPR export)
     */
    @Query("""
        SELECT c FROM Conversation c
        WHERE c.userLowId = :userId OR c.userHighId = :userId
        ORDER BY c.lastMessageAt DESC NULLS LAST
    """)
    List<Conversation> findAllByUserId(@Param("userId") UUID userId);

    /**
     * Count conversations for a user
     */
    @Query("""
        SELECT COUNT(c) FROM Conversation c
        WHERE c.userLowId = :userId OR c.userHighId = :userId
    """)
    long countByUserId(@Param("userId") UUID userId);

    /**
     * Delete conversations where both users have been deleted
     * (cleanup orphaned conversations)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        DELETE FROM Conversation c
        WHERE (c.userLowId = :userId OR c.userHighId = :userId)
          AND NOT EXISTS (
              SELECT 1 FROM Message m WHERE m.conversationId = c.id
          )
    """)
    int deleteEmptyConversationsByUserId(@Param("userId") UUID userId);
}
