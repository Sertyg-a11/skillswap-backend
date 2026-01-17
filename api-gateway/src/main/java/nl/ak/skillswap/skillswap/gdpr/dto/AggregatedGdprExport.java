package nl.ak.skillswap.skillswap.gdpr.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregated GDPR export response containing data from all services.
 */
public record AggregatedGdprExport(
        UUID exportId,
        UUID userId,
        Instant exportedAt,
        Map<String, Object> serviceData,
        Map<String, String> errors
) {
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
