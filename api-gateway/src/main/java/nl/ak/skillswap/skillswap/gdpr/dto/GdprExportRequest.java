package nl.ak.skillswap.skillswap.gdpr.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Event requesting GDPR data export for a user.
 * Published to RabbitMQ, consumed by all services.
 */
public record GdprExportRequest(
        UUID correlationId,
        UUID userId,
        String userExternalId,
        Instant requestedAt
) {
    public static GdprExportRequest create(UUID userId, String userExternalId) {
        return new GdprExportRequest(
                UUID.randomUUID(),
                userId,
                userExternalId,
                Instant.now()
        );
    }
}
