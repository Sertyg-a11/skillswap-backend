package nl.ak.skillswap.messageservice.service.event;

import java.time.Instant;
import java.util.UUID;

public record MessageCreatedEvent(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        UUID recipientId,
        Instant createdAt
) {}