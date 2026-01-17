package nl.ak.skillswap.messageservice.gdpr.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event requesting GDPR data deletion for a user.
 * Published by API Gateway, consumed by all services.
 * This is a fire-and-forget event - services handle deletion independently.
 */
public record GdprDeletionRequest(
        UUID correlationId,
        UUID userId,
        String userExternalId,
        Instant requestedAt,
        DeletionType deletionType
) {
    public enum DeletionType {
        /**
         * Full deletion - remove all user data
         */
        FULL,

        /**
         * Anonymization - anonymize data but keep structure
         * (e.g., replace user ID with "deleted" in messages)
         */
        ANONYMIZE
    }
}
