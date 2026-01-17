package nl.ak.skillswap.messageservice.support;

import java.util.UUID;

/**
 * Holds the resolved user context for an authenticated request.
 * Contains both the database UUID (for internal use) and the external ID (Keycloak sub).
 *
 * @param databaseId The user's UUID in the user-service database
 * @param externalId The user's Keycloak subject ID
 * @param displayName The user's display name (optional, may be null)
 */
public record AuthenticatedUserContext(
        UUID databaseId,
        String externalId,
        String displayName
) {
    /**
     * Convenience constructor when display name is not available.
     */
    public AuthenticatedUserContext(UUID databaseId, String externalId) {
        this(databaseId, externalId, null);
    }
}
