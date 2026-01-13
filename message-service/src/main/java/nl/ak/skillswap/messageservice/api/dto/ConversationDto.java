package nl.ak.skillswap.messageservice.api.dto;


import java.time.Instant;
import java.util.UUID;

public record ConversationDto(
        UUID id,
        UUID otherUserId,
        Instant createdAt,
        Instant lastMessageAt,
        long unreadCount
) {}
