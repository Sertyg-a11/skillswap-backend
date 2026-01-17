package nl.ak.skillswap.userservice.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

/**
 * Test configuration that provides a mock JWT decoder for integration tests.
 */
@TestConfiguration
public class TestSecurityConfig {

    public static final String TEST_EXTERNAL_ID = "test-keycloak-sub-123";
    public static final String TEST_EMAIL = "test@example.com";
    public static final String TEST_USERNAME = "testuser";

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(TEST_EXTERNAL_ID)
                .claim("email", TEST_EMAIL)
                .claim("preferred_username", TEST_USERNAME)
                .claim("scope", "openid profile email")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    /**
     * Create a JWT token for a specific user for testing.
     */
    public static Jwt createTestJwt(String externalId, String email, String username) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(externalId)
                .claim("email", email)
                .claim("preferred_username", username)
                .claim("scope", "openid profile email")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
