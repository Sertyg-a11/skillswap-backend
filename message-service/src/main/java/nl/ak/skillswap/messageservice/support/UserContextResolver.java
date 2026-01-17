package nl.ak.skillswap.messageservice.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.messageservice.service.UserIdResolverService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated user context from a Spring Security Authentication.
 * Extracts the JWT token and resolves the Keycloak external ID to database UUID.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserContextResolver {

    private final UserIdResolverService userIdResolverService;

    /**
     * Resolve the authenticated user context from the authentication object.
     *
     * @param authentication The Spring Security authentication
     * @return The resolved user context with database UUID
     * @throws IllegalStateException if authentication is not a JWT token
     * @throws UserIdResolverService.UserResolutionException if user cannot be resolved
     */
    public AuthenticatedUserContext resolve(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("Unsupported authentication type: " +
                    (authentication != null ? authentication.getClass().getName() : "null"));
        }

        Jwt jwt = token.getToken();
        String externalId = jwt.getSubject();
        String bearerToken = "Bearer " + jwt.getTokenValue();

        if (externalId == null) {
            throw new IllegalStateException("JWT token has no subject claim");
        }

        return userIdResolverService.resolve(externalId, bearerToken);
    }

    /**
     * Extract just the external ID (Keycloak sub) from authentication without resolving.
     * Useful for cases where you only need the external ID.
     *
     * @param authentication The Spring Security authentication
     * @return The Keycloak subject ID
     */
    public String extractExternalId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("Unsupported authentication type");
        }
        return token.getToken().getSubject();
    }

    /**
     * Extract the bearer token from authentication.
     *
     * @param authentication The Spring Security authentication
     * @return The bearer token string (including "Bearer " prefix)
     */
    public String extractBearerToken(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("Unsupported authentication type");
        }
        return "Bearer " + token.getToken().getTokenValue();
    }
}
