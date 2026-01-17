package nl.ak.skillswap.userservice.gdpr.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event requesting GDPR data deletion for a user.
 * Published by API Gateway, consumed by all services.
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
}
