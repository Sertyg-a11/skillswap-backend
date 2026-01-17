package nl.ak.skillswap.skillswap.gdpr.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response containing GDPR export data from a service.
 * Received from RabbitMQ reply queue.
 */
public record GdprExportResponse(
        UUID correlationId,
        String serviceName,
        UUID userId,
        Instant exportedAt,
        boolean success,
        String errorMessage,
        Object data
) {}
