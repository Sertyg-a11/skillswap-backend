package nl.ak.skillswap.messageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.messageservice.support.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Service for validating user existence with Redis caching.
 * Prevents sending messages to non-existent users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserValidationService {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.user-service.url:http://localhost:8081}")
    private String userServiceUrl;

    /**
     * Validates that a user exists.
     * Results are cached in Redis to minimize calls to user-service.
     *
     * @param userId the user ID to validate
     * @return true if user exists
     * @throws NotFoundException if user doesn't exist
     */
    @Cacheable(value = "user-exists", key = "#userId", unless = "#result == false")
    public boolean validateUserExists(UUID userId) {
        log.debug("Validating user exists: {}", userId);

        try {
            RestClient client = restClientBuilder.baseUrl(userServiceUrl).build();

            // Get JWT token from security context
            String token = getCurrentToken();

            // HEAD request to check user existence without fetching full profile
            var requestSpec = client.head().uri("/api/users/{id}/exists", userId);
            if (token != null) {
                requestSpec.header("Authorization", "Bearer " + token);
            }
            requestSpec.retrieve().toBodilessEntity();

            log.debug("User {} validated successfully", userId);
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.debug("User {} not found", userId);
                throw new NotFoundException("User not found: " + userId);
            }
            log.error("Error validating user {}: {}", userId, e.getMessage());
            throw e;
        }
    }

    private String getCurrentToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        return null;
    }

    /**
     * Check if a user can receive messages (not blocked, active account, etc.).
     * This could be extended to check blocking relationships.
     */
    @Cacheable(value = "user-can-message", key = "#fromUserId + '-' + #toUserId", unless = "#result == false")
    public boolean canSendMessageTo(UUID fromUserId, UUID toUserId) {
        // Basic validation - ensure both users exist
        validateUserExists(toUserId);

        // TODO: Add blocking logic when implemented
        // For now, any existing user can receive messages
        return true;
    }
}
