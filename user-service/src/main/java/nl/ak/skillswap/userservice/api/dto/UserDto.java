package nl.ak.skillswap.userservice.api.dto;

import nl.ak.skillswap.userservice.domain.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserDto(
        UUID id,
        String externalId,
        String email,
        String displayName,
        String timeZone,
        String bio,
        boolean active,
        boolean allowMatching,
        boolean allowEmails,
        OffsetDateTime createdAt,
        OffsetDateTime deletedAt
) {
    public static UserDto from(User u) {
        return new UserDto(
                u.getId(),
                u.getExternalId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getTimeZone(),
                u.getBio(),
                u.isActive(),
                u.isAllowMatching(),
                u.isAllowEmails(),
                u.getCreatedAt(),
                u.getDeletedAt()
        );
    }

    /**
     * Create a DTO with limited public information (for viewing other users).
     * Hides sensitive data like email, externalId, and preferences.
     */
    public static UserDto fromPublic(User u) {
        return new UserDto(
                u.getId(),
                null,  // Hide external ID
                null,  // Hide email (privacy)
                u.getDisplayName(),
                u.getTimeZone(),
                u.getBio(),
                true,  // Always show as active (since we only return active users)
                true,  // Always show as matchable
                false, // Hide email preference
                u.getCreatedAt(),
                null   // Hide deletion status
        );
    }
}
