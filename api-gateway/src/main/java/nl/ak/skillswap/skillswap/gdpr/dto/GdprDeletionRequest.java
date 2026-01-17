package nl.ak.skillswap.skillswap.gdpr.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Event requesting GDPR data deletion for a user.
 * Published to RabbitMQ, consumed by all services.
 */
public record GdprDeletionRequest(
        UUID correlationId,
        UUID userId,
        String userExternalId,
        Instant requestedAt,
        DeletionType deletionType
) {
    public enum DeletionType {
        FULL,
        ANONYMIZE
    }

    public static GdprDeletionRequest create(UUID userId, String userExternalId, DeletionType type) {
        return new GdprDeletionRequest(
                UUID.randomUUID(),
                userId,
                userExternalId,
                Instant.now(),
                type
        );
    }
}
