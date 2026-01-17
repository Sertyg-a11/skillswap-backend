package nl.ak.skillswap.userservice.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.userservice.api.dto.UserSearchRequest;
import nl.ak.skillswap.userservice.api.dto.UserSearchResponse;
import nl.ak.skillswap.userservice.service.UserSearchService;
import nl.ak.skillswap.userservice.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for user search functionality.
 * Allows authenticated users to search for other users by username or skills.
 */
@Slf4j
@RestController
@RequestMapping("/api/users/search")
@RequiredArgsConstructor
public class SearchController {

    private final UserSearchService searchService;
    private final UserService userService;

    /**
     * Search for users by query.
     *
     * GET /api/users/search?q=java&type=SKILL
     * GET /api/users/search?q=john&type=USERNAME
     * GET /api/users/search?q=programming&type=ALL
     * GET /api/users/search?category=Programming&type=CATEGORY
     */
    @GetMapping
    public ResponseEntity<UserSearchResponse> searchUsers(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "type", required = false, defaultValue = "ALL") UserSearchRequest.SearchType type,
            Authentication auth
    ) {
        UUID currentUserId = getCurrentUserId(auth);

        UserSearchRequest request = new UserSearchRequest(query, category, type);
        UserSearchResponse response = searchService.search(currentUserId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * Search users with POST body (for complex queries).
     */
    @PostMapping
    public ResponseEntity<UserSearchResponse> searchUsersPost(
            @Valid @RequestBody UserSearchRequest request,
            Authentication auth
    ) {
        UUID currentUserId = getCurrentUserId(auth);
        UserSearchResponse response = searchService.search(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    private UUID getCurrentUserId(Authentication auth) {
        String externalId = auth.getName();
        return userService.syncFromKeycloak(
                externalId,
                extractEmail(auth),
                extractDisplayName(auth)
        ).getId();
    }

    private String extractEmail(Authentication auth) {
        if (auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            return jwt.getClaimAsString("email");
        }
        return null;
    }

    private String extractDisplayName(Authentication auth) {
        if (auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            String name = jwt.getClaimAsString("preferred_username");
            if (name == null || name.isBlank()) {
                name = jwt.getClaimAsString("name");
            }
            return name;
        }
        return null;
    }
}
