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
}
