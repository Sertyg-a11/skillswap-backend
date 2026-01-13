package nl.ak.skillswap.messageservice.support;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID userId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();

            // Most common: Keycloak "sub" is the user id
            String sub = jwt.getSubject();
            if (sub != null) return UUID.fromString(sub);

            // Fallback if you ever map differently:
            Object claim = jwt.getClaims().get("sub");
            if (claim != null) return UUID.fromString(String.valueOf(claim));
        }
        throw new IllegalStateException("Unsupported authentication type or missing user id");
    }
}

