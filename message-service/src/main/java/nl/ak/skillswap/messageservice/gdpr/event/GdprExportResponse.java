package nl.ak.skillswap.messageservice.gdpr.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Response containing GDPR export data from a service.
 * Sent back to API Gateway for aggregation.
 */
public record GdprExportResponse(
        UUID correlationId,
        String serviceName,
        UUID userId,
        Instant exportedAt,
        boolean success,
        String errorMessage,
        Object data  // Service-specific export data (e.g., GdprExportData)
) {
    public static GdprExportResponse success(UUID correlationId, String serviceName, UUID userId, Object data) {
        return new GdprExportResponse(
                correlationId,
                serviceName,
                userId,
                Instant.now(),
                true,
                null,
                data
        );
    }

    public static GdprExportResponse error(UUID correlationId, String serviceName, UUID userId, String errorMessage) {
        return new GdprExportResponse(
                correlationId,
                serviceName,
                userId,
                Instant.now(),
                false,
                errorMessage,
                null
        );
    }
}
