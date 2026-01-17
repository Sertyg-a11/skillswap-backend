package nl.ak.skillswap.messageservice.repository;



import io.lettuce.core.dynamic.annotation.Param;
import nl.ak.skillswap.messageservice.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    // First page (no cursor)
    List<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    // Cursor page: createdAt < before
    List<Message> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            UUID conversationId,
            Instant before,
            Pageable pageable
    );

    long countByConversationIdAndRecipientIdAndReadAtIsNull(UUID conversationId, UUID recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Message m
           set m.readAt = :readAt
         where m.conversationId = :conversationId
           and m.recipientId = :recipientId
           and m.readAt is null
    """)
    int markConversationRead(
            @Param("conversationId") UUID conversationId,
            @Param("recipientId") UUID recipientId,
            @Param("readAt") Instant readAt
    );

    // ==================== GDPR Operations ====================

    /**
     * Find all messages sent by a user (for GDPR export)
     */
    List<Message> findBySenderId(UUID senderId);

    /**
     * Find all messages received by a user (for GDPR export)
     */
    List<Message> findByRecipientId(UUID recipientId);

    /**
     * Anonymize messages sent by user (GDPR deletion - keeps conversation for recipient)
     * Sets senderId to null and optionally clears body
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Message m
           set m.senderId = null,
               m.body = '[Message from deleted user]'
         where m.senderId = :userId
    """)
    int anonymizeMessagesBySender(@Param("userId") UUID userId);

    /**
     * Delete all messages received by user (GDPR deletion - removes user's inbox)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Message m where m.recipientId = :userId")
    int deleteMessagesByRecipient(@Param("userId") UUID userId);

    /**
     * Count messages sent by user
     */
    long countBySenderId(UUID senderId);

    /**
     * Count messages received by user
     */
    long countByRecipientId(UUID recipientId);
}
