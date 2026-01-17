package nl.ak.skillswap.messageservice.support;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Utility class for extracting user information from JWT authentication.
 *
 * Note: For most use cases, prefer using {@link UserContextResolver} which
 * resolves the Keycloak external ID to database UUID. This class provides
 * low-level access to JWT claims when needed.
 */
public final class CurrentUser {
    private CurrentUser() {}

    /**
     * Extract the external ID (Keycloak subject) from authentication.
     *
     * @param authentication The Spring Security authentication
     * @return The Keycloak subject ID (external ID)
     */
    public static String externalId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            String sub = token.getToken().getSubject();
            if (sub != null) return sub;
        }
        throw new IllegalStateException("Unsupported authentication type or missing subject");
    }

    /**
     * Extract the bearer token from authentication.
     *
     * @param authentication The Spring Security authentication
     * @return The bearer token string (including "Bearer " prefix)
     */
    public static String bearerToken(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return "Bearer " + token.getToken().getTokenValue();
        }
        throw new IllegalStateException("Unsupported authentication type");
    }

    /**
     * Extract the raw JWT token value from authentication.
     *
     * @param authentication The Spring Security authentication
     * @return The JWT token value (without "Bearer " prefix)
     */
    public static String tokenValue(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken().getTokenValue();
        }
        throw new IllegalStateException("Unsupported authentication type");
    }

    /**
     * Get the JWT object for more detailed claim access.
     *
     * @param authentication The Spring Security authentication
     * @return The JWT object
     */
    public static Jwt jwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new IllegalStateException("Unsupported authentication type");
    }
}

