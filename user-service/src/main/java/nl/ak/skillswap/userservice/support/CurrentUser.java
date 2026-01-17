package nl.ak.skillswap.userservice.support;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CurrentUser {
    private CurrentUser() {}

    public static String externalId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken t) {
            return t.getToken().getSubject();
        }
        throw new IllegalStateException("Unsupported authentication");
    }

    public static String email(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken t) {
            return t.getToken().getClaimAsString("email");
        }
        return null;
    }

    public static String preferredUsername(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken t) {
            return t.getToken().getClaimAsString("preferred_username");
        }
        return null;
    }
}