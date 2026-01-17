package nl.ak.skillswap.messageservice.api.dto;

import nl.ak.skillswap.messageservice.domain.Message;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(
        UUID id,
        UUID conversationId,
        UUID senderId,
        UUID recipientId,
        String body,
        Instant createdAt,
        Instant readAt
) {
    public static MessageDto from(Message m) {
        return new MessageDto(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                m.getRecipientId(),
                m.getBody(),
                m.getCreatedAt(),
                m.getReadAt()
        );
    }
}
