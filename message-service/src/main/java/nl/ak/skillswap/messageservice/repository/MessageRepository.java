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
}
