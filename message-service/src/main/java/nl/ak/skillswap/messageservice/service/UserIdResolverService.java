package nl.ak.skillswap.messageservice.service;

import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.messageservice.support.AuthenticatedUserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

/**
 * Service to resolve Keycloak external IDs (sub) to database UUIDs.
 * Results are cached in Redis to minimize calls to user-service.
 */
@Slf4j
@Service
public class UserIdResolverService {

    private final WebClient webClient;

    public UserIdResolverService(
            @Value("${app.user-service.url}") String userServiceUrl,
            WebClient.Builder webClientBuilder
    ) {
        this.webClient = webClientBuilder
                .baseUrl(userServiceUrl)
                .build();
    }

    /**
     * Resolve a Keycloak external ID to database UUID.
     * Results are cached for 24 hours.
     *
     * @param externalId The Keycloak subject ID
     * @param bearerToken The user's JWT token for authentication
     * @return The resolved user context with database UUID
     * @throws UserResolutionException if the user cannot be resolved
     */
    @Cacheable(value = "user-id-mapping", key = "#externalId")
    public AuthenticatedUserContext resolve(String externalId, String bearerToken) {
        log.debug("Resolving external ID to database UUID: {}", externalId);

        try {
            IdResolutionResponse response = webClient.get()
                    .uri("/api/users/resolve/{externalId}", externalId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .bodyToMono(IdResolutionResponse.class)
                    .block();

            if (response == null) {
                throw new UserResolutionException("Empty response from user-service");
            }

            log.debug("Resolved external ID {} to database ID {}", externalId, response.databaseId());
            return new AuthenticatedUserContext(
                    response.databaseId(),
                    response.externalId(),
                    response.displayName()
            );
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("User not found for external ID: {}", externalId);
                throw new UserResolutionException("User not found: " + externalId);
            }
            log.error("Failed to resolve user ID: {} - {}", externalId, e.getMessage());
            throw new UserResolutionException("Failed to resolve user: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error resolving user ID: {} - {}", externalId, e.getMessage());
            throw new UserResolutionException("Failed to resolve user: " + e.getMessage());
        }
    }

    /**
     * Response from user-service /resolve/{externalId} endpoint.
     */
    private record IdResolutionResponse(UUID databaseId, String externalId, String displayName) {}

    /**
     * Exception thrown when user ID resolution fails.
     */
    public static class UserResolutionException extends RuntimeException {
        public UserResolutionException(String message) {
            super(message);
        }
    }
}
