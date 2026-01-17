package nl.ak.skillswap.userservice.gdpr.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event requesting GDPR data export for a user.
 * Published by API Gateway, consumed by all services.
 */
public record GdprExportRequest(
        UUID correlationId,
        UUID userId,
        String userExternalId,
        Instant requestedAt
) {}
