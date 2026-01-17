package nl.ak.skillswap.messageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;
import java.util.UUID;

/**
 * Client for internal service-to-service calls to user-service.
 * Used for GDPR operations where no user JWT is available.
 */
@Slf4j
@Service
public class InternalUserServiceClient {

    private final WebClient webClient;

    public InternalUserServiceClient(
            @Value("${app.user-service.url}") String userServiceUrl,
            WebClient.Builder webClientBuilder
    ) {
        this.webClient = webClientBuilder
                .baseUrl(userServiceUrl)
                .build();
    }

    /**
     * Resolve external ID (Keycloak sub) to database UUID.
     * Calls user-service's internal endpoint (no auth required).
     *
     * @param externalId The Keycloak subject ID
     * @return The database UUID, or empty if user not found
     */
    public Optional<UUID> resolveExternalIdToDatabaseId(String externalId) {
        log.debug("Resolving external ID to database UUID: {}", externalId);

        try {
            IdResolutionResponse response = webClient.get()
                    .uri("/internal/users/resolve/{externalId}", externalId)
                    .retrieve()
                    .bodyToMono(IdResolutionResponse.class)
                    .block();

            if (response != null && response.databaseId() != null) {
                log.debug("Resolved external ID {} to database ID {}", externalId, response.databaseId());
                return Optional.of(response.databaseId());
            }
            return Optional.empty();
        } catch (WebClientResponseException.NotFound e) {
            log.warn("User not found for external ID: {}", externalId);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to resolve external ID {}: {}", externalId, e.getMessage());
            return Optional.empty();
        }
    }

    private record IdResolutionResponse(UUID databaseId, String externalId, String displayName) {}
}
