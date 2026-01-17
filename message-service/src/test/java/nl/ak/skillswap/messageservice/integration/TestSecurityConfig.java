package nl.ak.skillswap.messageservice.integration;

import nl.ak.skillswap.messageservice.service.RateLimitingService;
import nl.ak.skillswap.messageservice.service.UserValidationService;
import nl.ak.skillswap.messageservice.support.AuthenticatedUserContext;
import nl.ak.skillswap.messageservice.support.UserContextResolver;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Test configuration that provides mock JWT decoder and UserContextResolver for integration tests.
 */
@TestConfiguration
public class TestSecurityConfig {

    public static final UUID TEST_USER_DATABASE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final String TEST_EXTERNAL_ID = "test-keycloak-sub-123";
    public static final String TEST_EMAIL = "test@example.com";

    public static final UUID TEST_USER_2_DATABASE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final String TEST_USER_2_EXTERNAL_ID = "test-keycloak-sub-456";

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(TEST_EXTERNAL_ID)
                .claim("email", TEST_EMAIL)
                .claim("scope", "openid profile email")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Bean
    @Primary
    public UserContextResolver testUserContextResolver() {
        UserContextResolver mockResolver = Mockito.mock(UserContextResolver.class);

        // Default behavior: return test user 1
        when(mockResolver.resolve(any())).thenReturn(new AuthenticatedUserContext(
                TEST_USER_DATABASE_ID,
                TEST_EXTERNAL_ID,
                "Bearer test-token"
        ));

        return mockResolver;
    }

    @Bean
    @Primary
    public UserValidationService testUserValidationService() {
        UserValidationService mockService = Mockito.mock(UserValidationService.class);

        // Default behavior: all users exist and can receive messages
        when(mockService.validateUserExists(any())).thenReturn(true);
        when(mockService.canSendMessageTo(any(), any())).thenReturn(true);

        return mockService;
    }

    @Bean
    @Primary
    public RateLimitingService testRateLimitingService() {
        RateLimitingService mockService = Mockito.mock(RateLimitingService.class);

        // Default behavior: no rate limiting in tests
        doNothing().when(mockService).checkMessageRateLimit(any());
        doNothing().when(mockService).checkConnectionRateLimit(any());
        doNothing().when(mockService).checkTypingRateLimit(any());

        return mockService;
    }

    /**
     * Create a test JWT for a specific user.
     */
    public static Jwt createTestJwt(String externalId) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(externalId)
                .claim("email", "test@example.com")
                .claim("scope", "openid profile email")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
