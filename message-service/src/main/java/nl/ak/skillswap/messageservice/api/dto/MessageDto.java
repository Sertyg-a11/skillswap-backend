package nl.ak.skillswap.messageservice.api.dto;

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
) {}
