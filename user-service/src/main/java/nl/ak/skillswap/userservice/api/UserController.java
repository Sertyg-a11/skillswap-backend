package nl.ak.skillswap.userservice.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nl.ak.skillswap.userservice.api.dto.UpdatePreferencesRequest;
import nl.ak.skillswap.userservice.api.dto.UpdateProfileRequest;
import nl.ak.skillswap.userservice.api.dto.UserDto;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.repository.UserRepository;
import nl.ak.skillswap.userservice.service.InputSanitizer;
import nl.ak.skillswap.userservice.service.UserService;
import nl.ak.skillswap.userservice.support.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final InputSanitizer inputSanitizer;

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        User u = sync(authentication);
        return UserDto.from(u);
    }

    /**
     * Check if a user exists (for message-service validation).
     * Checks both by database ID and external ID (Keycloak sub) for flexibility.
     * Returns 200 if exists, 404 if not.
     */
    @RequestMapping(value = "/{id}/exists", method = RequestMethod.HEAD)
    public ResponseEntity<Void> userExists(@PathVariable UUID id) {
        // Check by database ID first, then by external ID (stored as String)
        boolean exists = userRepository.existsByIdAndDeletedAtIsNullAndActiveTrue(id)
                || userRepository.existsByExternalIdAndDeletedAtIsNullAndActiveTrue(id.toString());
        return exists ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    /**
     * Resolve external ID (Keycloak sub) to database UUID.
     * Internal endpoint for message-service to resolve user IDs.
     */
    @GetMapping("/resolve/{externalId}")
    public ResponseEntity<IdResolutionResponse> resolveId(
            @PathVariable String externalId,
            Authentication authentication
    ) {
        // Ensure requester is authenticated
        sync(authentication);

        return userRepository.findByExternalId(externalId)
                .filter(user -> user.getDeletedAt() == null)
                .filter(User::isActive)
                .map(user -> ResponseEntity.ok(new IdResolutionResponse(
                        user.getId(),
                        user.getExternalId(),
                        user.getDisplayName()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    public record IdResolutionResponse(UUID databaseId, String externalId, String displayName) {}

    /**
     * Get public profile of a user (for viewing other users).
     * Supports lookup by database ID or external ID (Keycloak sub) for compatibility with message-service.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserProfile(@PathVariable UUID id, Authentication authentication) {
        // Ensure requesting user is authenticated
        sync(authentication);

        // Try by database ID first, then by external ID (message-service uses Keycloak sub as user ID)
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .or(() -> userRepository.findByExternalIdAndDeletedAtIsNull(id.toString()))
                .filter(User::isActive)
                .filter(User::isAllowMatching)  // Only show if user allows matching
                .map(user -> ResponseEntity.ok(UserDto.fromPublic(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me/profile")
    public UserDto updateProfile(Authentication authentication, @Valid @RequestBody UpdateProfileRequest req) {
        User me = sync(authentication);

        // OWASP: Sanitize input to prevent XSS
        String sanitizedDisplayName = inputSanitizer.sanitizeText(req.displayName());
        String sanitizedBio = req.bio() != null ? inputSanitizer.sanitizeText(req.bio()) : null;

        User updated = userService.updateProfile(me.getId(), sanitizedDisplayName, req.timeZone(), sanitizedBio);
        return UserDto.from(updated);
    }

    @PutMapping("/me/preferences")
    public UserDto updatePreferences(Authentication authentication, @Valid @RequestBody UpdatePreferencesRequest req) {
        User me = sync(authentication);
        User updated = userService.updatePreferences(me.getId(), req.allowMatching(), req.allowEmails());
        return UserDto.from(updated);
    }

    @DeleteMapping("/me")
    public void deleteMe(Authentication authentication) {
        User me = sync(authentication);
        userService.softDeleteAccount(me.getId());
    }

    private User sync(Authentication authentication) {
        String sub = CurrentUser.externalId(authentication);
        String email = CurrentUser.email(authentication);
        String username = CurrentUser.preferredUsername(authentication);
        return userService.syncFromKeycloak(sub, email, username != null ? username : "User");
    }
}
