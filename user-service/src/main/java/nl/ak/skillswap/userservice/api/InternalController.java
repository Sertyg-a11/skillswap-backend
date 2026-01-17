package nl.ak.skillswap.userservice.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal endpoints for service-to-service communication.
 * These endpoints are not exposed through the API Gateway and are
 * secured by network policy (only accessible within the cluster).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalController {

    private final UserRepository userRepository;

    /**
     * Resolve external ID (Keycloak sub) to database UUID.
     * Used by message-service for GDPR operations.
     */
    @GetMapping("/users/resolve/{externalId}")
    public ResponseEntity<IdResolutionResponse> resolveId(@PathVariable String externalId) {
        log.debug("Internal ID resolution request for: {}", externalId);

        return userRepository.findByExternalId(externalId)
                .filter(user -> user.getDeletedAt() == null)
                .filter(User::isActive)
                .map(user -> {
                    log.debug("Resolved {} to {}", externalId, user.getId());
                    return ResponseEntity.ok(new IdResolutionResponse(
                            user.getId(),
                            user.getExternalId(),
                            user.getDisplayName()
                    ));
                })
                .orElseGet(() -> {
                    log.warn("User not found for external ID: {}", externalId);
                    return ResponseEntity.notFound().build();
                });
    }

    public record IdResolutionResponse(UUID databaseId, String externalId, String displayName) {}
}
